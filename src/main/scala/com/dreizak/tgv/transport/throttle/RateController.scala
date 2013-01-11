package com.dreizak.tgv.transport.throttle

import java.util.concurrent.{ ScheduledExecutorService, TimeUnit }
import scala.annotation.implicitNotFound
import scala.collection.immutable.Queue
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import com.dreizak.util.concurrent.Cancellable
import scala.concurrent.CanAwait
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.util.Try
import scala.concurrent.ExecutionContext
import com.weiglewilczek.slf4s.Logging
import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.util.concurrent.CancellableFuture.cancellable

/**
 * A helper class useful for implementing higher-level throttling: execute at most <i>n</i> tasks per
 * <i>t</i> seconds.
 *
 * <em>Example:</em> You need to call an external web-service <i>S</i> that may only be called 5 times in 2
 * seconds. Here, the HTTP request to <i>S</i> is the task at hand. To be on the safe side, we define the task to
 * start <i>before</i> you make the actual request and end <i>after</i> you receive the entire response (and not
 * after you sent the request!). In this way, since [[com.dreizak.tgv.transport.throttle.RateController]] ensures that our tasks are
 * not executed more than 5 times in 2 seconds, we are sure to not call the web service too
 * frequently.
 *
 * == Guarantees ==
 * This class ensures that on the timeline, all intervals `[t, t+rate.durationInMillis)`, for any `t`, overlap at
 * most `rate.numberOfCalls`-many tasks. Here, a task is an interval `[s,e)`, where `s` is the time when the `throttle`
 * method's `Receipt` has been provided (i.e., the future returned by method `throttle` has completed) and where `e`
 * is the time when the `completed` method was called on this `Receipt` instance. Tasks for which the `completed` method
 * has not yet been called have the right end `e` of their interval at time infinity; such tasks are called
 * <em>outstanding</em>.
 *
 * The execution order of throttled tasks may not coincide with the order in which `throttle` is called.
 *
 * This class is thread-safe.
 *
 * == Usage ==
 * To execute a task ''t'' in a throttled way, call the throttler's `throttle` method. This method
 * returns a Scala future providing a <em>task receipt</em>. Once the future completes, you can start the
 * task ''t''. (Notice that you <em>must</em> wait until the future actually completes; do <em>not</em>
 * already start the task ''t'' when the method `throttle` returns.) Once your tasks has ended, you must
 * eventually call `r.completed()` &mdash; do it as soon as possible in order to allow the throttler
 * to schedule other tasks.
 *
 * It is allowed to call a receipt's `completed` method more than once (only the first invocation has
 * an effect).
 *
 * Forgetting to call the `completed` method will entail a resource leak. `RateController`s provide
 * a `timeout` parameter to "auto-complete" receipts for which the `completed` method has not been
 * called after a period of length `timeout`. You <em>should not</em> rely on this method as your
 * throttler may run at a very much reduced rate if you do so.
 *
 * == Blocking ==
 * This class is designed in such a way that you can use it without blocking any thread.
 * (Note: the class internally uses a lock to achieve thread-safety; blocking on this lock may happen
 * but will be for extremely small periods of time only.) The method `throttle` returns a future
 * and the caller can decide whether to use blocking (by calling Scala's `Await.result` method) or
 * employ an asynchronous callback.
 *
 * == Cancellation ==
 * `RateController`s provide support for <em>cancellation</em> of submitted tasks, albeit with the
 * following limitation: at any time, you can cancel all but at most `rate.numberOfTasks`-many of the
 * tasks that have been submitted to the rate controller and that have not yet started.
 *
 * You cancel a task by calling `cancel(reason)` on the future returned by the rate controller's `throttle`
 * method. Observe thate ven if you cancel a future obtained via `throttle`, the future <em>may</em>
 * complete with a `Receipt`. In this case, the cancellation request has not succeeded (either because it
 * came too late or due to the limited cancellation support of `RateController`) and you <em>must</em>
 * eventually call `completed` on the receipt.
 *
 * == Remarks ==
 * Notice that this rate control implementation maintains to some extend the history of past tasks: it
 * may be necessary for the algorithm to keep up to `rate.numberOfCalls` history records in
 * memory. It is therefore not recommended to use this class if `rate.numberOfCalls` is
 * large.
 *
 * This fact together with the aforementioned cancellation limitation suggest that you should use
 * rates with low `rate.numberOfCalls`. For example, instead of a rate `1000 per (10 seconds)`,
 * you may want to use `10 per (100 millis)`.
 *
 * == Authors ==
 * Part of this code was taken from Charles Cordinc's blog post
 * <a href='http://www.cordinc.com/blog/2010/04/java-multichannel-asynchronous.html'>http://www.cordinc.com/blog/2010/04/java-multichannel-asynchronous.html</a>.
 * The extension to tasks `[s,e)` with `s != e` was written by <a href='mailto:kf@iaeth.ch'>Kaspar Fischer</a>.
 *
 * @param rate the maximal rate: message will be delivered at a rate never exceeding `rate`
 * @param executor a Scala execution context (used for the futures returned by `throttle`)
 * @param scheduler a scheduler used to run tasks with delays
 * @param timeProvider access to the current time (useful for mocking the time during unit tests)
 * @param timeout error timeout (see above)
 * @param verifyInvariants set this to `true` to run more expensive checks to validate invariants &mdash; at some
 * point, we might want to turn this off
 *
 * @author Kaspar Fischer (hbf)
 */
final class RateController(rate: Rate,
                           executor: ExecutionContext,
                           scheduler: RateController.Scheduler,
                           timeProvider: RateController.TimeProvider = new RateController.DefaultTimeProvider(),
                           timeout: FiniteDuration = 5 minutes,
                           verifyInvariants: Boolean = true) extends Logging {
  private val timeoutInMillis = timeout.toMillis

  /**
   * Number of tasks that are outstanding (i.e., whose `completed` method has not yet been called and
   * whose future has not been `cancel`'d).
   *
   * Note: this variable should only be accessed when the instance lock has been acquired.
   */
  private var outstanding = 0

  /**
   * When a new task is `throttle`'d, the implementation needs to distinguish two cases:
   *
   *  - The number of outstanding tasks is strictly smaller than `rate.numberOfCalls`: in this
   *    case we have all information at hand to compute the earliest possible start time for
   *    the new task.
   *
   *  - The number of outstanding tasks equals `rate.numberOfCalls`: here, we cannot possibly
   *    determine the earliest possible start time for the task to be submitted &mdash; at
   *    least one of the outstanding tasks needs to finish before we can calculate this.
   *
   * In the latter case we will postpone the scheduling task and put the request into the
   * queue `indeterminates`.
   *
   * Note: this variable should only be accessed when the instance lock has been acquired.
   */
  private var indeterminates = Queue[Promise[Receipt]]()

  /**
   * The task schedule: conceptually, we maintain the intervals of <i>all</i> tasks (in arbitrary
   * order) that we have scheduled (in the past or future) so as to ensure that no time interval of size
   * `rate.durationInMillis` overlaps more than `rate.numberOfCalls` tasks. Most of these tasks will have
   * a finite start- and end-time but at most `rate.numberOfCalls` among them may have an infinite
   * end-time; an infinite end-time is used to model tasks that have not yet ended (operation still
   * in progress, equivalently, the `completed` method has not yet been called).
   *
   * Notice that the task schedule will contain tasks from the past, tasks overlapping the current
   * time, and also tasks scheduled in the future.
   *
   * In reality, it suffices to keep only those tasks that overlap the interval
   * `(now-rate.durationInMillis,infinity)`, where `now` is the current time. If a new
   * task takes place, it must start at or after `now`, and by induction we know that without
   * that task, all intervals of size `rate.durationInMillis` on the timeline overlap at most
   * `rate.numberOfCalls` scheduled tasks (some of which may still be in progress
   * &mdash; remember that those end at time infinity in the future); so when we add the new task, the
   * number of tasks changes only in the intervals that contain the task, and these intervals
   * overlap at most tasks from the interval `(now-rate.durationInMillis,infinity)`. The method
   * `compact` below does exactly this: it removes from the task schedule those tasks that are
   * not needed anymore.
   *
   * Note: this variable should only be accessed when the instance lock has been acquired.
   */
  private var schedule: List[Receipt] = List()

  /**
   * Note: this method should only be called when the instance lock has been acquired.
   */
  private def checkInvariants(now: Long) = {
    // A task in the future cannot have ended already
    if (verifyInvariants) require(!schedule.exists(t => (t.startTime > now) && !t.active))

    // We can never have more than n outstanding tasks
    require(0 <= outstanding && outstanding <= rate.numberOfTasks)
  }

  def handleTimeouts() = {
    val now = timeProvider.now()
    val timedOutRequests = synchronized { // Note: volatile would be an alternative.
      schedule.filter(t => t.active && (now - t.startTime) >= timeoutInMillis)
    }
    timedOutRequests.foreach(t => {
      logger.error("Throttle request was not closed within " + timeout + ": " + t)
      t.completed()
    })
  }

  /**
   * The number of tasks that are currently outstanding.
   *
   * Notice that if this rate controller is accessed concurrently, the result of this method
   * may be out-dated the moment the method returns.
   *
   * This method is used for testing only.
   */
  private[throttle] def load(): Int = synchronized { outstanding }

  /**
   * Removes from the task schedule `schedule` all entries that can safely be ignored.
   *
   * Note: this method should only be called when the instance lock has been acquired.
   */
  private def compact(now: Long) = {
    // All tasks ending before or at this time can be ignored
    val threshold = now - rate.durationInMillis()
    schedule = schedule.filter(_.endTime > threshold)
  }

  /**
   * Prepare to start a throttle task.
   *
   * Call this method before a task to be throttled starts. Once the future returned by this
   * method provides a `Receipt`, you can start running the task. (Notice that you must
   * not already start the task when the `throttled` method returns &mdash; you must await the future's
   * result.) Once the task has completed, you must invoke the `completed` method of the
   * returned `Receipt` instance. (It is expliclity allowed to call `completed` several times;
   * only the first invocation will have an effect.)
   *
   * The result of this method &mdash; an instance, `t`, say, of type `CancellableFuture[Receipt]` &mdash;
   * provides a method `cancel` through which you can request the rate controller to "unschedule"
   * the task. If you call `cancel`, there is, however, no guarantee that the task will be
   * unscheduled. The rate controller only guarantees that it can cancel all but at most
   * `rate.numberOfCalls`-many tasks at any time.
   *
   * If you call `t.cancel(throwable)` and the rate controller can execute the cancellation
   * then the future `t.future`'s value will be `Failure(throwable)`.
   *
   * Refer to the class documentation for more information on the cancellation contract.
   */
  def throttle(): CancellableFuture[Receipt] = {
    handleTimeouts()

    // Now comes the actual work
    synchronized { throttleGuarded() }
  }

  private def throttleGuarded(): CancellableFuture[Receipt] = {
    val now = timeProvider.now()
    checkInvariants(now)

    val promise = Promise[Receipt]()
    val result = cancellable(promise)

    if (outstanding == rate.numberOfTasks) {
      indeterminates = indeterminates.enqueue(promise)
    } else {
      val nextEarliest = earliestStartTime(now)
      val receipt = Receipt(nextEarliest)
      schedule = receipt :: schedule
      outstanding = outstanding + 1

      scheduler.scheduleOnce((nextEarliest - now) millis, () => {
        if (!promise.trySuccess(receipt)) {
          // Here, we expect the promise to have failed due to cancellation (TODO: add test for this case)
          require(promise.future.value.get.isFailure, "Expected cancellation but found success; problem in RateController.")
          receipt.completed()
        }
      })
      // TODO: `scheduleOnce` returns a task that is cancellable, so we could remove the task
      // from the scheduler to spare resources
    }

    checkInvariants(now)
    result
  }

  /**
   * Determines the earliest possible start time for a next task to be scheduled
   * and returns a new [[com.dreizak.tgv.transport.throttle.Receipt]] instance for this time.
   * Note that this method adds the returned [[com.dreizak.tgv.transport.throttle.Receipt]] instance to
   * the task `schedule`.
   *
   * The method assumes that less than `rate.numberOfCalls` tasks are outstanding.
   *
   * Note: this method should only be called when the instance lock has been acquired.
   */
  private def earliestStartTime(now: Long): Long = {
    // Get rid of those tasks in the past that cannot influence our decision anymore
    compact(now)

    /*
     * We need to find the time t where we can insert a new task. By induction, all intervals of
     * size s=rate.durationInMillis do not overlap more than n=rate.numberOfCalls tasks. So if
     * we insert a task that starts at some time t, only the intervals of size s overlapping [t,
     * infinity) will have different number of tasks and therefore it suffices to check these
     * intervals. In other words, inserting at time t requires checking that no interval
     * I=[u,u+s) overlaps more than n tasks for all u in (t-s, infinity).
     * 
     * In the following, we distinguish two cases:
     */

    /*
     * Case when less than n tasks overlap (now-s, infinity): as a consequence of the above is, we can
     * insert the task at time now; all intervals containing now will then overlap at most n tasks.
     */
    if (schedule.size < rate.numberOfTasks) now

    // Case when (now-s, infinity) covers at least n tasks 
    else {
      /*
       * Let
       * 
       *   e = min { t | no task in the schedule starts after t }
       * 
       * be the moment in time past which no task in the schedule starts; e is finite because the
       * schedule is non-empty. --- The idea is to take the task in the interval [e-s,infinity)
       * that ends first.
       * 
       * (In the following, an interval [u,v) "overlaps" [x,y) iff their set intersection is non-empty;
       * [u,v) "overlaps or touches" [x,y) iff [u,v] set-intersects [x,y].)
       * 
       * Claim: Denote by N_0(t) the number of tasks that overlap or touch [t,infinity), and let
       * 
       *   c = argmin { c.end() | c is a task in the schedule, c.end() >= e-s }
       * 
       * be the (not necessarily unique but existing) task that ends first in [e-s,infinity).
       * If N_0(c.end()) < n then we can insert a task at time e. If N_0(c.end()) >= n then we can
       * insert a task at time c.end()+s.
       * 
       * Proof. Denote by n_0(I) the number of tasks that overlap the interval I before the new
       * task is inserted, and denote by n_1(I) the number of tasks that overlap the interval I
       * after the new task has been inserted. Notice first that by induction all intervals
       * I(u):=[u,u+s), u any number, overlap at most n tasks, i.e., we have n_0(I(u)) <= n for
       * any u. Also, note that N_0(t) is monotonously decreasing as t increases. Finally, note
       * that n_0([u,u+s)) <= N_0(u) for any u.
       * 
       * Case N_0(c.end()) < n: We insert at time e, so we only have to verify that n_1(I(u)) <= n
       * when u ranges from e-s to infinity. We claim that n_0(I(u)) < n for all u in
       * [e-s,infinity) and from this it will follow that n_1(I(u)) <= n, since we have added a
       * single task only. So suppose n_0([u,u+s)) >= n for some u in [e-s,infinity). We get
       * 
       *   N_0(u) >= n_0([u,u+s)) >= n.
       * 
       * If c.end() <= u, we have N_0(c.end()) >= N_0(u) >= n by monotonicity, a contradiction.
       * 
       * If c.end() > u then notice first that we can assume u>e-s: suppose u=e-s.
       * No task can end at e-s because if it did, c would be such a task, satisfying e-s=c.end(),
       * and we would have u = e-s = c.end() > u, contradiction. So we can assume u>e-s in
       * analyzing the case c.end() > u.
       * 
       * Notice that since c finishes first, (e-s,c.end()) only contains task starts, if any,
       * but no task ends. So as we have e-s < u < c.end(), we get N_0(u) <= N_0(c.end()) and
       * therefore
       * 
       *   n <= n_0([u,u+s)) <= N_0(u) <= N_0(c.end()) < n,
       * 
       * contradiction.
       * 
       * Case N_0(c.end()) = n: We insert at time c.end()+s, so we only have to verify that
       * n_1(I(u)) <= n when u ranges from c.end() to infinity. By induction, N_0(c.end()) can be
       * at most n. So n_0(I(c.end())) <= n, and adding a new task that starts at c.end()+s
       * does not violate this (because intervals are half-open). I(c.end()+eps) does not overlap
       * the task c but overlaps the new task so it covers again at most n tasks, by induction. A
       * sweep to the right can only decrease the number of overlapped tasks, because the right
       * side of the interval being swept is c.end()+s >= e, and past e, no new task starts. So
       * the remaining intervals I(u) are valid, too.
       * 
       * So:
       */

      // Find e
      val e = schedule.maxBy(_.startTime).startTime

      // Find c.end()
      val threshold = e - rate.durationInMillis
      val cEnd = schedule.filter(_.endTime >= threshold).minBy(_.endTime).endTime

      // Determine N_0(c.end())
      val nCEnd = schedule.count(_.endTime >= cEnd)

      val start = if (nCEnd < rate.numberOfTasks) e else cEnd + rate.durationInMillis + 1; // TODO

      start
    }
  }

  private[throttle] def completed(task: Receipt) = synchronized {
    val now = timeProvider.now()

    checkInvariants(now)
    require(schedule.contains(task));

    if (task.endAt(now)) {
      require(outstanding > 0)
      outstanding = outstanding - 1
    }

    // Get rid of cancelled requests
    val notCancelled = indeterminates.filter(!_.isCompleted)
    logger.trace("Removed " + (indeterminates.size - notCancelled.size) + " cancelled requests.")
    indeterminates = notCancelled

    // Schedule any indeterminate if possible
    if (indeterminates.size > 0) {
      val (promise, remaining) = indeterminates.dequeue
      indeterminates = remaining

      require(outstanding < rate.numberOfTasks) // Implies we won't enqueue to `indeterminates` again

      // Note: We use `tryComplete` instead of `complete` because in the meantime,
      // the user may have called `cancel`, which will have completed the promise.
      throttleGuarded().future.onComplete(r =>
        if (!promise.tryComplete(r)) {
          // Here, the user has cancelled the original promise (from the `indeterminates` queue)
          // and we therefore need to call `completed` ourselves.
          if (r.isSuccess) r.get.completed() // Fix for #14.
        })(executor)
    } else {
      checkInvariants(now)
    }
  }

  /**
   * Models a task for throttling purposes.
   *
   * Call the method `completed` to signal that the task has ended.
   *
   * @see [[com.dreizak.tgv.transport.throttle.RateController]]
   */
  final case class Receipt private[throttle] (private[throttle] val startTime: Long,
                                              private[throttle] var endTime: Long = Long.MaxValue) {
    require(endTime >= startTime);

    // Returns true iff the tasks has not been `endAt`'d before.
    private[throttle] def endAt(t: Long): Boolean = synchronized {
      val wasInfinite = endTime == Long.MaxValue
      endTime = t
      wasInfinite
    }

    /**
     * Whether the task has already been completed (using `completed`).
     */
    def active(): Boolean = synchronized { endTime == Long.MaxValue }

    /**
     * Signals to the throttler from which this `Receipt` has been obtained that
     * the task has been completed.
     */
    def completed(): Unit = RateController.this.completed(this)
  }
}

object RateController {
  /**
   * Returns the current system time in milliseconds (like `System.currentTimeMillis`).
   *
   * We do not use `System.currentTimeMillis` directly in order to be able to test the code.
   */
  trait TimeProvider {
    def now(): Long
  }

  /**
   * Provides the current time using `System.currentTimeMillis`.
   */
  class DefaultTimeProvider extends TimeProvider {
    def now() = System.currentTimeMillis
  }

  /**
   * Runs a task after a given delay.
   */
  trait Scheduler {
    def scheduleOnce(delay: FiniteDuration, runnable: () => Unit)
  }

  /**
   * A [[com.dreizak.tgv.transport.throttle.RateController.Scheduler]] that uses a
   * Java `ScheduledExecutorService` to schedule tasks.
   */
  class DefaultScheduler(implicit scheduler: ScheduledExecutorService) extends Scheduler {
    def scheduleOnce(delay: FiniteDuration, runnable: () => Unit) =
      scheduler.schedule(new Runnable() {
        override def run() = runnable()
      }, delay.toMillis, TimeUnit.MILLISECONDS)
  }
}
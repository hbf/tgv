package com.dreizak.tgv.transport.throttle

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import org.jmock.lib.concurrent.DeterministicScheduler
import org.junit.runner.RunWith
import org.scalatest.{ BeforeAndAfterEach, Suite, WordSpec }
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.transport.throttle.RateController.{ DefaultScheduler, Scheduler, TimeProvider }
import com.dreizak.util.concurrent.CancelledException
import org.scalatest.junit.JUnitRunner
import scala.util.Failure
import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.util.concurrent.CancellableFuture.{ await => awaitCancellable }
import scala.concurrent.Future.sequence
import com.dreizak.util.concurrent.CancelledException
import scala.concurrent.Future
import scala.concurrent.Await
import com.dreizak.tgv.infrastructure.testing.ExecutionContextForEach
import com.dreizak.tgv.infrastructure.testing.TestingUtils.await

@RunWith(classOf[JUnitRunner])
class RateControllerSpec extends Suite with WordSpec with MustMatchers with BeforeAndAfterEach with ExecutionContextForEach {

  class TestingTimeProvider(s: DeterministicScheduler) extends TimeProvider {
    private var t = 0
    def now() = t
    def tick() = {
      s.tick(1, TimeUnit.MILLISECONDS)
      t = t + 1
    }
  }

  implicit var detScheduler: DeterministicScheduler = null
  var scheduler: Scheduler = null
  var time: TestingTimeProvider = null
  implicit var deterministicContext: ExecutionContext = null

  override def beforeEach = {
    super.beforeEach()
    detScheduler = new DeterministicScheduler()
    scheduler = new DefaultScheduler()
    time = new TestingTimeProvider(detScheduler)
    deterministicContext = ExecutionContext.fromExecutor(detScheduler, _ => throw new IllegalStateException("!"))
  }

  def create(numberOfTasks: Int, durationInMs: Int, timeout: FiniteDuration = 1 hour) = {
    new RateController(Rate(numberOfTasks, durationInMs millis),
      deterministicContext,
      scheduler,
      time, timeout, verifyInvariants = false) // TODO #15: the deterministic scheduler makes some invariants fail.
  }

  /**
   * Given a set of tuples (id, `startTime`, `duration`, `cancelTime`), schedules for each quadruple
   * a task of the given duration at the given time. Notice that `startTime` is the time when the task
   * is scheduled; it may actually begin later. If `cancelTime` is non-negative, the task will be
   * canceled at this time.
   *
   * @return a set of triples holding the id, and the actual start- and end-time of the tasks
   */
  def schedule(throttler: RateController, tasks: Set[(Int, Long, Long, Long)], end: Int): Set[(Int, Long, Long)] = {
    var history = Set[(Int, Long, Long)]() // id, start, stop
    var toEnd = List[((Int, Long, Long, Long), Long, Long, throttler.Receipt)]() // task, eff. start, eff. stop, receipt
    var toCancel = List[((Int, Long, Long, Long), Long, CancellableFuture[throttler.Receipt])]() // task, cancel, throttled

    for (i <- (0L to end)) {
      // println("Time: " + i)

      // Cancel tasks
      toCancel.filter(_._2 == i).foreach({
        case (task, cancel, throttled) =>
          println("Canceling task " + task + " at time " + i + ".")
          throttled.cancel(new CancelledException("Cancelled."))
      })

      // Finish tasks
      toEnd.filter(_._3 == i).foreach({
        case (task, effStart, effStop, receipt) =>
          println("Finishing task " + task + ", effectively started at " + effStart + " at time " + i + ".")
          val e = (task._1, effStart, effStop)
          history = history + e
          receipt.completed()
      })

      // Schedule tasks
      tasks.filter(_._2 == i).foreach({
        case t @ (id, start, d, cancel) =>
          println("Scheduling task " + t + " (duration " + d + ") at time " + i + ".")
          val throttled = throttler.throttle()
          throttled.future.map(receipt => {
            val startTime = time.now()
            println("Started task " + t + " (duration " + d + ") at time " + startTime + ".")
            toEnd = (t, startTime, startTime + d, receipt) :: toEnd
          })(deterministicContext)
          if (cancel >= 0) {
            toCancel = (t, cancel, throttled) :: toCancel
          }
      })
      time.tick()
    }
    println(history)
    history
  }

  def cancelTest(numberOfTasks: Int, N: Int) = {
    val throttler = create(numberOfTasks, 30)

    val ran = schedule(throttler,
      // N tasks of duration 20, all to be started at time 0 (only numberOfTasks-many will actually run) 
      (1 to N).map(i => (i, 0L, 20L, 1L)).toSet ++
        // ... plus one task at time 10, which will start at 20+30=50
        Set((N + 1, 2L, 10L, -1L)),
      200
    ).toList.sortBy(_._1) // Sort by id

    ran.take(numberOfTasks).foreach { l =>
      // Must have started at 0 and ended at 20
      l._2 must be(0)
      l._3 must be(20)
    }

    ran.drop(numberOfTasks)(0) must equal((N + 1, 50L, 60L))
    ran must have size (numberOfTasks + 1)
  }

  "A rate controller" must {
    "handle a simple example" in {
      val throttler = create(2, 5)
      schedule(throttler,
        Set((1, 0, 3, -1), (2, 3, 3, -1), (3, 6, 3, -1), (4, 9, 3, -1), (5, 12, 3, -1), (6, 15, 3, -1), (7, 18, 3, -1)),
        30
      ) must equal(
          Set((1, 0, 3), (2, 3, 6), (3, 8, 11), (4, 11, 14), (5, 16, 19), (6, 19, 22), (7, 24, 27))
        )
    }

    "not throttle at all when the actual rate is below the threshold rate" in {
      val throttler = create(3, 30)
      val N = 30
      schedule(throttler,
        (1 to N).map(i => (i, i * 20L, 20L, -1L)).toSet,
        20 * (N + 1) + 1
      ) must equal(
          (1 to N).map(i => (i, i * 20L, (i + 1) * 20L)).toSet
        )
    }

    "cancel all but `rate.numberOfTasks`-many calls - case 3" in {
      cancelTest(3, 100)
    }

    "cancel all but `rate.numberOfTasks`-many calls - case 4" in {
      cancelTest(4, 100)
    }

    "cancel all but `rate.numberOfTasks`-many calls - case 5" in {
      cancelTest(5, 100)
    }

    "cancel all but `rate.numberOfTasks`-many calls - case 10" in {
      cancelTest(10, 100)
    }

    "provide the cause for cancelled tasks" in {
      val throttler = create(1, 1)
      val t1 = throttler.throttle() // will be scheduled immediately
      val t2 = throttler.throttle() // will have to wait, so we can cancel it
      val cause = new IllegalStateException("foo")
      t2.cancel(cause)
      t2.future.isCompleted must be(true)
      val thrown = evaluating { await(t2.future) } must produce[CancelledException]
      thrown.getCause must equal(cause)
    }

    // Notice that the following test will and should print error messages to the log
    // like this: "ERROR - Throttle request was not closed within 6 milliseconds"
    "time out when requests are not closed" in {
      val throttler = create(100, 100, 6 millis)
      val t1 = throttler.throttle()
      // We won't call receipt.completed()!
      time.tick()
      throttler.throttle()
      throttler.load() must be(2)
      time.tick()
      throttler.throttle()
      throttler.load() must be(3)
      time.tick()
      throttler.throttle()
      throttler.load() must be(4)
      time.tick()
      throttler.throttle()
      throttler.load() must be(5)
      time.tick()
      throttler.throttle()
      throttler.load() must be(6)
      time.tick()
      // Time is 6 here.
      throttler.throttle()
      // We have made 7 `throttle` calls, but the first has timed out:
      throttler.load() must be(7 - 1)
      (1 to 6).toList.foreach { i => time.tick() }
      throttler.throttle().future.map(receipt => receipt.completed())(deterministicContext)
      time.tick()
      throttler.load() must be(0)
    }

    "schedule the next tasks immediately after congestion" in {
      val throttler = create(5, 1)
      val fs = (1 to 5).toList.map(_ => throttler.throttle())
      val f = throttler.throttle()
      time.tick()
      fs.foreach(_.future.isCompleted must be(true))
      f.future.isCompleted must be(false)
      fs.map(f => awaitCancellable(f.map(_.completed())(executionContext)))
      time.tick()
      time.tick()
      time.tick() // TODO: needed because of the "+1" in earliestStartTime()
      throttler.load() must be(1)
      f.future.isCompleted must be(true)
    }

    "not fail if already scheduled tasks get cancelled" in {
      val throttler = create(1, 2)
      val f1 = throttler.throttle()
      time.tick()
      throttler.load() must be(1)
      awaitCancellable(f1.map(_.completed())(executionContext))
      val f2 = throttler.throttle() // Will be scheduled in a few ticks only.
      time.tick()
      throttler.load() must be(1)
      f2.cancel(new CancelledException())
      time.tick()
      time.tick()
      throttler.load() must be(0)
    }

    /**
     * In #14, the bug was that when the throttler is congested (i.e., all tasks outstanding) and
     * you submit another tasks, this task will be put in the `indeterminates` queue. If you cancel
     * the task before it gets dequeued from the queue then, at the time of the dequeuing, the
     * task gets actually scheduled and the rate controller in the end completes the
     * promise of the future initially returned to the client -- expecting that the client would
     * then call `completed` at some point. But the client cancelled and will not do that.
     */
    "not fail if already enqueued ('indeterminate') tasks get cancelled [regression #14]" in {
      val throttler = create(5, 2)
      val congestors = (1 to 5).toList.map(_ => throttler.throttle())
      val successors = (1 to 5).toList.map(_ => throttler.throttle())
      time.tick()
      throttler.load must be(5)
      congestors.foreach(_.future.isCompleted must be(true))
      successors.foreach(_.future.isCompleted must be(false))
      congestors.foreach(f => awaitCancellable(f.map(_.completed())(executionContext))) // Schedules the successors to run a few ticks later.
      time.tick()
      throttler.load must be(5)
      successors.foreach(_.future.isCompleted must be(false))
      successors.foreach(_.cancel(new CancelledException()))
      // During one of the following ticks, the successors will complete (which manifested the bug)
      time.tick()
      time.tick()
      throttler.load must be(0)
    }

    "not fail if `completed` is called more than once" in {
      val throttler = create(10, 1)
      val tasks = (1 to 4).toList.map(_ => throttler.throttle())
      time.tick()
      throttler.load must be(4)
      tasks.foreach(_.future.isCompleted must be(true))
      tasks.foreach(f => awaitCancellable(f.map(_.completed())(executionContext)))
      tasks.foreach(f => awaitCancellable(f.map(_.completed())(executionContext)))
      time.tick()
      throttler.load must be(0)
    }
  }
}
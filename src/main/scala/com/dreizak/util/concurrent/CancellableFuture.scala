package com.dreizak.util.concurrent

import java.util.concurrent.TimeUnit

import scala.concurrent.{ Await, ExecutionContext, Future, Promise }
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import com.dreizak.tgv.SchedulingContext

/**
 * A Scala future that is cancellable.
 *
 * An instance of this class wraps an ordinary Scala future, which is available as `future`. In addition,
 * it provides methods for <em>cancellation</em>.
 *
 * The cancellation support provided by `CancellableFuture` is a mere <em>signaling</em> mechanism: you can
 * use the `onCancellation` construct to register a callback to be invoked when the future is cancelled.
 * The actual abort of the task represented by the future is up to the implementation.
 *
 * TODO: following needs updating and reviewing
 *
 *  - The companion object's `cancellable(p)` factory method provides an implementation that  simply fails the given promise `p`
 *    with the cause `t` of the `cancel(t)` cancellation.
 *  - Alternatlvely, you can use the `cancellable(f) { op }` factory method of the `CancellableFuture` companion, which
 *    takes a future `f` and a cancellation operation `op`; the latter will be exectured (at most once) when the
 *    future returned by `cancellable(f) { op }` is cancelled.
 *
 * For example, imagine you want to execute a task with a delay, by scheduling on a Java `ScheduledExecutorService`,
 * and want its result to be available in form of a `CancellableFuture`:
 *
 * {{{
 *   // TODO: check whether this is still up-to-date
 *   val promise = Promise[Result]()
 *   val receipt: ScheduledFuture[Result] = scheduler.schedule(
 *     new Callable[Result]() {
 *       override def call(): Result = {
 *         // Note: example ignores failure handling (`promise.faulure(...)`).
 *         promise.trySuccess(...) // Note: don't use `success`.
 *       }
 *     }, 10, TimeUnit.SECONDS)
 *   val f = cancellable(promise)
 * }}}
 *
 * Notice that you should use `promise.trySuccess` and not `promise.success` because
 * both the updater of the future (in this case the scheduled task) and a potential
 * canceller are both "competing" for completing the promise. (`cancellable` uses
 * `tryFailure` internally.)
 *
 * The cancellation flows in the opposite direction of values and is propagated
 * by combinators like `map`, `flatMap`, etc. For example, in
 *
 * {{{
 * val f = f1 map { r => g(r) } flatMap { r => f2(r) }
 * }}}
 *
 * values flow from left to right. If you call `cancel` on `f`, the cancellation
 * signal flows from right to left until it arrives at `f1`. The `cancel` method of `f1`
 * will do whatever is appropriate to abort the task.
 *
 * == Implementation notes ==
 * The code is influenced substantially by the comments on a
 * <a href='https://groups.google.com/forum/#!msg/akka-user/eXiBV5V7ZzE/TUis_9pB4S8J'>post</a>
 * in the Akka User Group.
 */
trait CancellableFuture[+T] extends Cancellable {
  private implicit def internalExecutor: ExecutionContext = CancellableFuture.InternalCallbackExecutor

  def future: Future[T]

  /**
   * When this future is cancelled, invoke the given callback `op`.
   *
   * @return self for chaining
   */
  def onCancellation(op: Throwable => Unit): CancellableFuture[T]

  import CancellableFuture.cancellable

  private def wrap[S](g: Future[S]): CancellableFuture[S] =
    cancellable(g).onCancellation(CancellableFuture.this.cancel)

  def failed: CancellableFuture[Throwable] = wrap(future.failed)

  def transform[S](s: T => S, f: Throwable => Throwable)(implicit executor: ExecutionContext): CancellableFuture[S] =
    wrap(future.transform(s, f)(executor))

  def step[S](f: Try[T] => S)(implicit executor: ExecutionContext): CancellableFuture[S] = {
    val p = Promise[S]()
    val result = wrap(p.future)

    future.onComplete {
      case res =>
        try {
          p success f(res)
        } catch {
          case NonFatal(t) => p failure t
        }
    }(executor)

    result
  }

  def stepWith[S](f: Try[T] => CancellableFuture[S])(implicit executor: ExecutionContext): CancellableFuture[S] = {
    val p = Promise[S]()
    val result = wrap(p.future)

    future.onComplete {
      case res =>
        try {
          val intermediate = f(res)

          result.onCancellation(intermediate.cancel _)

          intermediate.future.onComplete {
            case Failure(t) => p failure t
            case Success(r) => p success r
          }(internalExecutor)
        } catch {
          case NonFatal(t) => p failure t
        }
    }(executor)

    result
  }

  def mapFailure(f: Throwable => Throwable)(implicit executor: ExecutionContext): CancellableFuture[T] =
    transform(identity[T], f)(executor)

  def map[S](f: T => S)(implicit executor: ExecutionContext): CancellableFuture[S] =
    wrap(future.map(f)(executor))

  def flatMap[S](f: T => Future[S])(implicit executor: ExecutionContext): CancellableFuture[S] =
    wrap(future.flatMap(f)(executor))

  def cancellableFlatMap[S](f: T => CancellableFuture[S])(implicit executor: ExecutionContext): CancellableFuture[S] =
    // Code taken from Scala source, modified appropriately.
    {
      val p = Promise[S]()
      val result = wrap(p.future)

      future.onComplete {
        case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
        case Success(v) =>
          try {
            val intermediate = f(v)

            result.onCancellation(intermediate.cancel _)

            intermediate.future.onComplete({
              case f: Failure[_] => p complete f.asInstanceOf[Failure[S]]
              case Success(v) => p success v
            })(internalExecutor)
          } catch {
            case NonFatal(t) => p failure t
          }
      }(executor)

      result
    }

  def filter(pred: T => Boolean)(implicit executor: ExecutionContext): CancellableFuture[T] =
    wrap(future.filter(pred)(executor))

  def withFilter(p: T => Boolean)(implicit executor: ExecutionContext): CancellableFuture[T] =
    wrap(future.withFilter(p)(executor))

  def collect[S](pf: PartialFunction[T, S])(implicit executor: ExecutionContext): CancellableFuture[S] =
    wrap(future.collect(pf)(executor))

  def recover[U >: T](pf: PartialFunction[Throwable, U])(implicit executor: ExecutionContext): CancellableFuture[U] =
    wrap(future.recover(pf)(executor))

  def recoverWith[U >: T](pf: PartialFunction[Throwable, Future[U]])(implicit executor: ExecutionContext): CancellableFuture[U] =
    wrap(future.recoverWith(pf)(executor))

  def recoverWith[U >: T](pf: PartialFunction[Throwable, CancellableFuture[U]])(implicit executor: ExecutionContext, d: DummyImplicit): CancellableFuture[U] =
    // Code taken from Scala source, modified appropriately.
    {
      val p = Promise[U]()
      val result = wrap(p.future)

      future.onComplete {
        case Failure(t) if pf isDefinedAt t =>
          try {
            val intermediate = pf(t)

            result.onCancellation(intermediate.cancel _)

            p completeWith intermediate.future
          } catch {
            case NonFatal(t) => p failure t
          }
        case otherwise => p complete otherwise
      }(executor)

      result
    }

  // Exists because using `flatMap` causes the Scala compiler to complain about ambiguity.
  def cancellableRecoverWith[U >: T](pf: PartialFunction[Throwable, CancellableFuture[U]])(implicit executor: ExecutionContext): CancellableFuture[U] =
    recoverWith(pf)(executor, DummyImplicit.dummyImplicit)

  def zip[U](that: Future[U]): CancellableFuture[(T, U)] =
    wrap(future.zip(that))

  def zip[U](that: CancellableFuture[U])(implicit d: DummyImplicit): CancellableFuture[(T, U)] = ???

  def fallbackTo[U >: T](that: Future[U]): CancellableFuture[U] =
    wrap(future.fallbackTo(that))

  def fallbackTo[U >: T](that: CancellableFuture[U])(implicit d: DummyImplicit): CancellableFuture[U] = ???

  def mapTo[S](implicit tag: ClassTag[S]): CancellableFuture[S] =
    wrap(future.mapTo)

  def andThen[U](pf: PartialFunction[Try[T], U])(implicit executor: ExecutionContext): CancellableFuture[T] =
    wrap(future.andThen(pf)(executor))
}

/**
 * Base class for implementing [[com.dreizak.util.concurrent.CancellableFuture]]s.
 *
 * TODO: document: you can cancel even when `future` is completed...
 */
private[concurrent] final class CancellableFutureImpl[T](val future: Future[T]) extends CancellableFuture[T] {
  private implicit def internalExecutor: ExecutionContext = CancellableFuture.InternalCallbackExecutor

  /**
   * Will be completed with the cause of the cancellation (from the `cancel(cause)` method); all
   * `onCancellation` methods are registered via `onFailure` on `cancelled`. Notice that `cancelled`
   * completes with a failure iff `cancel` is called; it completes with a success otherwise.
   */
  private val cancelled = Promise[Unit]()

  // Ensure that we don't call references to all the cancellation callbacks once the future completes successfully.
  future.onSuccess { case _ => cancelled.trySuccess(()) }

  def onCancellation(op: Throwable => Unit): CancellableFuture[T] = {
    cancelled.future.onFailure { case t => op(t) }(internalExecutor)
    this
  }

  override def isCancelled() = cancelled.future.value.map(_.isFailure).getOrElse(false)

  override def cause() = {
    val v = cancelled.future.value
    require(v != None, "Method cause() on cancellable that was not cancelled.")
    v.get.failed.get
  }

  override def cancel(cause: Throwable) = cancelled.tryFailure(cause)
}

/**
 * Factory methods for constructing cancellable futures.
 */
object CancellableFuture {
  private[concurrent] object InternalCallbackExecutor extends ExecutionContext {
    override def execute(runnable: Runnable): Unit = runnable.run()
    override def reportFailure(t: Throwable): Unit =
      throw new IllegalStateException("problem in com.dreizak.util.concurrent internal callback", t)
  }

  /**
   * Creates a [[com.dreizak.util.concurrent.CancellableFuture]] from a given Scala `Future`.
   * You can use `onCancellation` to schedule a callback to be executed on cancellation.
   */
  def cancellable[T](f: Future[T]): CancellableFuture[T] = new CancellableFutureImpl(f)

  /**
   * Creates a [[com.dreizak.util.concurrent.CancellableFuture]] from
   * a given Scala `Promise`. When the future is cancelled, with cause `t`, say, the promise will be
   * set to `Failue(t)` in case `t` is a [[com.dreizak.util.concurrent.CancelledException]] and
   * to
   * {{{
   * Failure(CancelledException("Cancelled due to failure.", t))
   * }}}
   * otherwise.
   */
  def cancellable[T](p: Promise[T]): CancellableFuture[T] =
    new CancellableFutureImpl(p.future).onCancellation { cause =>
      p.tryFailure(cause match {
        case e: CancelledException => e
        case _ => new CancelledException("Cancelled due to failure.", cause)
      })
    }

  /**
   * A cancellable future whose `cancel` method does not do anything.
   */
  def notCancellable[T](f: Future[T]): CancellableFuture[T] = new CancellableFuture[T] {
    val future = f
    override def cancel(cause: Throwable) = false
    override def cause() = throw new IllegalStateException("isCancelled() not called, which returns false")
    override def isCancelled() = false
    override def onCancellation(op: Throwable => Unit) = this
  }

  /**
   * Like `Future.successful` but returns a `CancellableFuture`.
   */
  def successful[T](v: T): CancellableFuture[T] = new CancellableFutureImpl(Future.successful(v))

  /**
   * Like `Future.failed` but returns a `CancellableFuture`.
   */
  def failed[T](t: Throwable): CancellableFuture[T] = new CancellableFutureImpl(Future.failed(t))

  /**
   * Creates a cancellable future that completes the earliest after the given delay.
   */
  def delay(timespan: Duration)(implicit context: SchedulingContext): CancellableFuture[Unit] = {
    val promise = Promise[Unit]()
    val receipt = context.schedule(new Runnable() {
      override def run() {
        promise.success(())
      }
    }, timespan.toMillis, TimeUnit.MILLISECONDS)
    cancellable(promise).onCancellation(cause => receipt.cancel(true))
  }

  /**
   * Short-hand for `Await.result(f.future, timeout)`.
   */
  def await[T](f: CancellableFuture[T])(implicit timeout: Duration): T = Await.result(f.future, timeout)

  /**
   * Short-hand for `Await.result(f.future, timeout)`.
   */
  def result[T](f: CancellableFuture[T])(implicit timeout: Duration): T = Await.result(f.future, timeout)

  /**
   * Short-hand for `Await.ready(f.future, timeout)`.
   */
  def ready[T](f: CancellableFuture[T])(implicit timeout: Duration) = Await.ready(f.future, timeout)

  implicit class TryToFuture[T](val t: Try[T]) extends AnyVal {
    /**
     * Creates a `CancellableFuture` that is completed with the value or failure of `t`, respectively.
     */
    def asCancellable() =
      t match {
        case Success(s) => successful(s)
        case Failure(f) => failed(f)
      }
  }
}
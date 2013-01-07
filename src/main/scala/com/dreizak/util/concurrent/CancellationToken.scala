package com.dreizak.util.concurrent

import scala.annotation.implicitNotFound
import scala.concurrent.{ ExecutionContext, Promise }

/**
 * A token allowing you to cancel an operation.
 *
 * The creator of `CancellationToken` must call `hasSucceeded` in case the operation is <em>not</em>
 * cancelled. This is mandatory and serves to unregister any `onCancellation` listeners. (TODO:
 * is this really necessary? One could argue that when you dispose of the cancellation token,
 * its listeners will get released, too. But a lot of intermediary state may be "blocked" in memory
 * by not freeing them as early as possible.)
 *
 * Notice that a `CancellationToken` is normally only handled by the code that creates the token;
 * users should be returned the token as a `Cancellable` in order to ensure that `hasSucceeded`
 * can only be called by the creator.
 *
 * TODO: document edge case
 */
class CancellationToken extends Cancellable {
  private implicit def internalExecutor: ExecutionContext = CancellableFuture.InternalCallbackExecutor

  /**
   * Will be completed with the cause of the cancellation (from the `cancel(cause)` method); all
   * `onCancellation` methods are registered via `onFailure` on `cancelled`. `cancelled`
   * completes with a failure iff `cancel` is called; it eventually completes with a success
   * iff the task succeeds.
   *
   * Notice that it is important that `cancelled` gets completed, eventually, both in case
   * of success and in failure; this will remove the dependencies on the listeners and allow
   * them to be garbage-collected.
   */
  private val cancelled = Promise[Unit]()

  def onCancellation(op: Throwable => Unit): CancellationToken = {
    cancelled.future.onFailure { case t => op(t) }
    this
  }

  override def isCancelled() = cancelled.future.value.map(_.isFailure).getOrElse(false)

  override def cause() =
    cancelled.future.value.
      ensuring(_ != None, "Method cause() on cancellable that was not cancelled.").
      get.failed.get

  override def cancel(cause: Throwable) = cancelled.tryFailure(cause)

  def hasSucceeded(): Boolean = cancelled.trySuccess(())
}

object CancellationToken {
  private[concurrent] object InternalCallbackExecutor extends ExecutionContext {
    override def execute(runnable: Runnable) = runnable.run()
    override def reportFailure(t: Throwable) =
      throw new IllegalStateException("Problem in com.dreizak.util.concurrent internal callback; please file a bug report.", t)
  }
}
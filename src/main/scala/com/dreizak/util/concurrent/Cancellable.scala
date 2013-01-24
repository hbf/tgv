package com.dreizak.util.concurrent

import scala.concurrent.Promise

/**
 * A task ("operation", "process", etc.) that can be canceled.
 *
 * `Cancellable`s are thread-safe.
 *
 * `Cancellable`s are used in [[com.dreizak.util.concurrent.CancellableFuture]], for example.
 */
trait Cancellable {
  /**
   * Cancels the task.
   *
   * If `cancelled()` is already true before the call, this method has no effect.
   *
   * Notice that in the lifetime of a `Cancellable`, `cancel` will return `true`
   * at most once.
   *
   * `Cancellable`s that do not support cancellation will return false from this method.
   *
   * @param reason for cancellation
   * @return `true` iff the instance  could be cancelled by this invocation of `cancel`
   */
  def cancel(cause: Throwable): Boolean

  /**
   * True iff the task represented by this object has been aborted.
   */
  def isCancelled(): Boolean

  /**
   * The cause of the cancellation.
   *
   * The cause is only defined if `cancelled()` is `true`.
   */
  def cause(): Throwable
}


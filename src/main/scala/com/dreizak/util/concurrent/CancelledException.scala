package com.dreizak.util.concurrent

/**
 * Thrown when a [[com.dreizak.util.concurrent.Cancellable]] that has been `cancel`'d
 * is acted upon.
 *
 * For example, when a [[com.dreizak.tgv.transport.Transport]] processes a request
 * for which its `CancellationManager` reports that it has been cancelled, the result of the computation
 * is a `CancelledException`.
 */
class CancelledException(msg: String = "Operation cancelled.", cause: Throwable = null) extends RuntimeException(msg, cause)
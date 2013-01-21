package com.dreizak.tgv.transport

import scala.util.{ Failure, Success }
import com.dreizak.tgv.SchedulingContext
import com.dreizak.tgv.transport.backoff.BackoffStrategy
import com.dreizak.tgv.transport.transform.Transform
import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.util.concurrent.CancellableFuture.{ delay, successful }
import com.weiglewilczek.slf4s.Logging

/**
 * A utility class that may be used to implement [[com.dreizak.tgv.transport.Transport]]s.
 *
 * Users should <em>not</em> use this class but call [[com.dreizak.tgv.transport.Transport]]'s
 * `withRetryStrategy` method to obtain a `Transport` with retry capabilities.
 */
private[transport] class Retrying[Req <: TransportRequest](retryStrategy: RetryStrategy, backoffStrategy: BackoffStrategy) extends Transform[Req] with Logging {

  def apply(req: Req, client: Client[Req])(implicit context: SchedulingContext): CancellableFuture[(Headers, Array[Byte])] =
    forward(client, req, backoffStrategy)

  /**
   * Submits the given request to `handler` and once the request completes &mdash; successfully
   * or with a failure &mdash; invokes the retry strategy to see whether the request should be retried.
   */
  def forward(client: Client[Req], request: Req, backoffStrategy: BackoffStrategy)(implicit context: SchedulingContext): CancellableFuture[(Headers, Array[Byte])] = {
    client.submit(request).cancellableRecoverWith {
      case cause if retryStrategy.shouldRetry(cause) && backoffStrategy.escalate().shouldRetry =>
        logger.info(s"Request ${request} failed, retrying (retry strategy: ${retryStrategy}; back-off strategy: ${backoffStrategy}).")
        delay(backoffStrategy.delay).cancellableFlatMap(_ => forward(client, request, backoffStrategy.escalate()))
    }
  }
}
package com.dreizak.tgv.transport.retry

import scala.concurrent.duration._

/**
 * Determines the timing between successive retries when a request of a [[com.dreizak.tgv.transport.Transport]]
 * fails.
 *
 * A backoff strategy is a immutable object that determines whether or not a request should
 * be retried (see `shouldRetry`) and if so, after how long a delay (see `delay`).
 */
trait BackoffStrategy {
  /**
   * Returns the strategy to use when the current request has failed or failed again.
   */
  def escalate(): BackoffStrategy

  /**
   * Determines whether the request should be retried.
   *
   * If this method returns `true`, the request `retryRequest` will be retried after at least the delay
   * `delay`.
   */
  def shouldRetry: Boolean

  /**
   * Determines the minimum delay before the request is retried.
   *
   * This method is only defined in case `shouldRetry` returns `true`.
   */
  def delay: Duration

  /**
   * The number of this attempt; the first retry has number 1, the second 2, etc.
   *
   * This method is only defined in case `shouldRetry` returns `true`.
   */
  def attempt: Int
}
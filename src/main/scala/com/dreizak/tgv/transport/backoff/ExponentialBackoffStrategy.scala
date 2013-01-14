package com.dreizak.tgv.transport.backoff

import scala.concurrent.duration.{ Duration, DurationInt }
import scala.math.random

import com.dreizak.tgv.transport.TransportDefinition

/**
 * A [[com.dreizak.tgv.transport.backoff.BackoffStrategy]] that retries a fixed number
 * of times with an exponentially growing, perturbed delay.
 *
 * See the companion object for factory methods and additional documentation.
 */
final class ExponentialBackoffStrategy private[backoff] (
  val attempt: Int,
  remainingRetries: Int,
  val delay: Duration,
  private[backoff] val delayWithoutRandomziation: Duration, // Note: exposed for testing. 
  multiplier: Double,
  randomizer: Double) extends BackoffStrategy {

  def shouldRetry = remainingRetries >= 0

  def escalate() = {
    val newDelayWithoutRandomziation = delayWithoutRandomziation * multiplier
    new ExponentialBackoffStrategy(attempt + 1,
      remainingRetries - 1,
      newDelayWithoutRandomziation * (1.0 + (2 * random - 1) * randomizer),
      newDelayWithoutRandomziation,
      multiplier,
      randomizer)
  }

  override def toString() = "[delay:" + delay.toMillis + "ms,attempt:" + attempt + ",remaining after this:" + remainingRetries + "]"
}

/**
 * Factory methods.
 */
object ExponentialBackoffStrategy {
  /**
   * Creates a [[com.dreizak.tgv.transport.backoff.ExponentialBackoffStrategy]] that
   * will retry at most `maxRetries` times. Each time, the delay will grow following an exponential
   * growth function.
   *
   * The delay for the first retry is `delay`. For subsequent retries, the retry delay is computed
   * as follows. Let ''hp'' be the <em>hidden delay</em> of the previous retry; then the hidden delay
   * ''h'' of the next retry will be
   *
   * {{{
   *  h = hp * multiplier
   * }}}
   *
   * The actual delay of the next retry will be
   *
   * <pre>
   * hp * (random value in range [1 - randomizer, 1 + randomizer])
   * </pre>
   *
   * The default initial delay is 500ms. The default multiplier is 1.5 and default randomizer 0.5.
   * The hidden delay is defined to be `delay/multiplier`, so the default hidden delay is 333.33ms.
   * Therefore, if `maxRetries` were 9, the following retries would be attempted:
   * <pre>
   * retry       hidden delay (s)     actual delay (s)
   *
   * 1           0.50                 [0.25,  0.75]
   * 2           0.75                 [0.38,  1.13]
   * 3           1.13                 [0.56,  1.69]
   * 4           1.69                 [0.84,  2.53]
   * 5           2.53                 [1.27,  3.80]
   * 6           3.80                 [1.90,  5.70]
   * 7           5.70                 [2.85,  8.54]
   * 8           8.54                 [4.27, 12.81]
   * 9           12.81                [6.41, 19.22]
   * </pre>
   * </p>
   */
  def exponentialBackoffStrategy(maxRetries: Int, delay: Duration = 500 millis, multiplier: Double = 1.5, randomizer: Double = 0.5) =
    new ExponentialBackoffStrategy(0, maxRetries, delay / multiplier, delay / multiplier, multiplier, randomizer)
}
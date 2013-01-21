package com.dreizak.tgv.transport.backoff

import scala.concurrent.duration._
import scala.math.random

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
   * Therefore, if `maxRetries` were 30, the following retries would be attempted:
   * <pre>
   * retry       hidden delay (s)     actual delay (s)
   *   1             0.50             [    0.25,     0.75]
   *   2             0.75             [    0.38,     1.13]
   *   3             1.13             [    0.56,     1.69]
   *   4             1.69             [    0.84,     2.53]
   *   5             2.53             [    1.27,     3.80]
   *   6             3.80             [    1.90,     5.70]
   *   7             5.70             [    2.85,     8.54]
   *   8             8.54             [    4.27,    12.81]
   *   9            12.81             [    6.41,    19.22]
   *  10            19.22             [    9.61,    28.83]
   *  11            28.83             [   14.42,    43.25]
   *  12            43.25             [   21.62,    64.87]
   *  13            64.87             [   32.44,    97.31]
   *  14            97.31             [   48.65,   145.96]
   *  15           145.96             [   72.98,   218.95]
   *  16           218.95             [  109.47,   328.42]
   *  17           328.42             [  164.21,   492.63]
   *  18           492.63             [  246.32,   738.95]
   *  19           738.95             [  369.47,  1108.42]
   *  20          1108.42             [  554.21,  1662.63]
   *  21          1662.63             [  831.31,  2493.94]
   *  22          2493.94             [ 1246.97,  3740.91]
   *  23          3740.91             [ 1870.46,  5611.37]
   *  24          5611.37             [ 2805.69,  8417.06]
   *  25          8417.06             [ 4208.53, 12625.58]
   *  26         12625.58             [ 6312.79, 18938.38]
   *  27         18938.38             [ 9469.19, 28407.56]
   *  28         28407.56             [14203.78, 42611.35]
   *  29         42611.35             [21305.67, 63917.02]
   *  30         63917.02             [31958.51, 95875.53]
   * </pre>
   */
  def exponentialBackoffStrategy(maxRetries: Int, delay: Duration = 500 millis, multiplier: Double = 1.5, randomizer: Double = 0.5) =
    new ExponentialBackoffStrategy(0, maxRetries, delay / multiplier, delay / multiplier, multiplier, randomizer)
}
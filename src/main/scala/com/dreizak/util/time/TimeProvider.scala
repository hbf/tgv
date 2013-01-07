package com.dreizak.util.time

/**
 * Provides the current system time in milliseconds (like `System.currentTimeMillis`).
 *
 * Classes make use of tis trait when they do not want to use `System.currentTimeMillis` directly,
 * in order to be able to test the code.
 */
trait TimeProvider {
  def now(): Long
}

object TimeProvider {
  /**
   * Provides the current time using `System.currentTimeMillis`.
   */
  def systemTimeProvider() = new TimeProvider {
    def now() = System.currentTimeMillis
  }
}

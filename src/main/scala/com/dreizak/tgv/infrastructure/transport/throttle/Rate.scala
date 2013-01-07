package com.dreizak.tgv.infrastructure.transport.throttle

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
 * A rate used for throttling.
 *
 * @param numberOfTasks the number of tasks that may take place in a period
 * @param duration the length of the period
 * @see [[com.dreizak.tgv.infrastructure.transport.Transport]]
 * @see [[com.dreizak.tgv.infrastructure.transport.throttle.RateController]]
 */
case class Rate(val numberOfTasks: Int, val duration: FiniteDuration) {
  def durationInMillis(): Long = duration.toMillis
}

object Rate {
  implicit class ToRate(val i: Int) extends AnyVal {
    def per(d: FiniteDuration) = new Rate(i, d)
  }
  implicit class ToRateString(s: String) {
    private val Format = """\s*(\d+)\s*/\s*(\d+)\s*""".r

    /**
     * Assumes the given string matches the format `nrOfCalls/millis`, where `nrOfCalls` and `millis`
     * are numbers, possibly surrounded by whitespace, and returns the rate `nrOfCalls` calls in `millis` milliseconds.
     */
    def asTasksPerMs() = {
      val Format(no, ms) = s
      Rate(no.toInt, ms.toInt millis)
    }
  }
}
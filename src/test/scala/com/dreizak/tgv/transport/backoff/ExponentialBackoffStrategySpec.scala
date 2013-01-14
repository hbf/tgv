package com.dreizak.tgv.transport.backoff

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.junit.JUnitRunner

import com.dreizak.tgv.transport.backoff.ExponentialBackoffStrategy.exponentialBackoffStrategy;

import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.annotation.tailrec

@RunWith(classOf[JUnitRunner])
class ExponentialBackoffStrategySpec extends WordSpec with MustMatchers {
  "An exponential retry strategy" must {
    "must not retry if maxRetries=0" in {
      val s = exponentialBackoffStrategy(0)
      s.attempt must be(0)
      val r1 = s.escalate()
      r1.shouldRetry must be(false)
      r1.attempt must be(1)
    }
    "must retry exactly once if maxRetries=1" in {
      val s = exponentialBackoffStrategy(1)
      s.attempt must be(0)
      val r1 = s.escalate()
      r1.attempt must be(1)
      r1.shouldRetry must be(true)
      r1.delayWithoutRandomziation must be(500 millis)
      val r2 = r1.escalate()
      r2.attempt must be(2)
      r2.shouldRetry must be(false)
    }
    "must retry exactly nine times if maxRetries=9" in {
      var s = exponentialBackoffStrategy(9)
      s.attempt must be(0)
      for (i <- (1 to 9)) {
        s = s.escalate()
        s.shouldRetry must be(true)
        s.delayWithoutRandomziation must be(delay(i))
        s.attempt must be(i)
      }
      s = s.escalate()
      s.shouldRetry must be(false)
    }
  }

  def delay(retry: Int): Duration = if (retry == 1) 0.5 seconds else 1.5 * delay(retry - 1)
}
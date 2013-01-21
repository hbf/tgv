package com.dreizak.tgv.transport.http

import scala.concurrent.Await.result
import scala.concurrent.Future.sequence
import scala.concurrent.duration.DurationInt
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.infrastructure.testing.{ ExecutionContextForEach, MockServer }
import com.dreizak.tgv.transport.http.HttpTransport.toRequest
import com.dreizak.tgv.transport.throttle.Rate.ToRate
import com.dreizak.util.concurrent.CancelledException
import com.google.common.base.Strings
import com.dreizak.tgv.infrastructure.testing.MultiJvmTestBarrier

trait BaseLoadBehavior {
  this: WordSpec with MustMatchers with ExecutionContextForEach with MultiJvmTestBarrier =>

  val transport: HttpTransport

  def httpTransportUnderLoadMultiJvm(barrier: String, port: Int) = {
    "make 50k requests at 1k/sec" in {
      // waitForDebugging()
      enterBarrier(BaseLoadBehavior.this, barrier)
      val N = 1 //10000

      val atMost1kParallelRequests = transport.withThrottling(1000 per (1 milli))

      val reqs = (1 to N).map { _ => transport.body(transport.getBuilder(s"http://localhost:${port}/")).future }

      val results = result(sequence(reqs), 100 seconds)
      results must have size (N)
      results.toSet must be(Set("yes"))
      exitBarrier(BaseLoadBehavior.this, barrier)
    }
  }

  def httpTransportUnderCancellationLoadMultiJvm(barrier: String, port: Int) = {
    "make 5k requests and cancel (most of them)" in {
      // waitForDebugging()
      enterBarrier(BaseLoadBehavior.this, barrier)
      val N = 0 //5000

      val atMost1kParallelRequests = transport.withThrottling(10 per (100 millis))
      def makeReq() = transport.body(transport.getBuilder(s"http://localhost:${port}/long"))
      val response = Strings.repeat("yes", 10000)

      println("Making " + N + " requests...")
      val reqs = (1 to N).map { _ => makeReq() }

      println("Cancelling them all...")
      reqs.foreach(_.cancel(new CancelledException()))

      println("Waiting...")
      result(sequence(reqs.map(_.future)), 100 seconds).foreach(_ must equal(response))

      result(makeReq().future, 1 second) must equal(response)
      exitBarrier(BaseLoadBehavior.this, barrier)
    }
  }

  def waitForDebugging() = {
    import java.lang.management.ManagementFactory
    println("Waiting to start... (pid " + ManagementFactory.getRuntimeMXBean().getName() + ")...")
    Thread.sleep(20000)
    println("Starting...")
  }
}
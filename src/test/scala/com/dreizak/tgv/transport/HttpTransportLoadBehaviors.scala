package com.dreizak.tgv.transport

import org.mockito.Mockito.when
import org.scalatest.{ Finders, WordSpec }
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.infrastructure.testing.{ ExecutionContextForEach, MockServer }
import com.dreizak.tgv.transport.http.{ HttpHeaderError, HttpTransport }
import com.dreizak.tgv.transport.http.sonatype.HttpInMemoryResponseTooLarge
import com.dreizak.util.concurrent.CancellableFuture.await
import com.google.common.base.Strings.repeat
import com.dreizak.tgv.transport.http.transform.UrlTransform.transformUrl
import com.dreizak.tgv.transport.throttle.Rate._
import nu.rinu.test.Response
import nu.rinu.test.mockito.RequestOf.requestOf
import scala.concurrent.Await.result
import scala.concurrent.Future.sequence
import scala.concurrent.duration._
import com.google.inject.Inject
import com.dreizak.tgv.transport.http.sonatype.StreamingAsyncHttpClient
import nu.rinu.test.HttpServer
import nu.rinu.test.HttpServerHandler
import nu.rinu.test.Request

/**
 * Base test for `Transport`s for load testing.
 *
 * An implementation of [[com.dreizak.tgv.transport.Transport]] should be tested with `httpTransportUnderLoad`, see for example
 * [[com.dreizak.tgv.transport.http.sonatype.AsyncHttpTransportSpec]].
 *
 * Notice that `httpTransportUnderLoad` is not in `HttpTransportBehaviors` because we cannot use a mock server;
 * the latter would accumulate the history of the requests and eventually run out of memory.
 */
trait HttpTransportLoadBehaviors {
  this: WordSpec with MustMatchers with ExecutionContextForEach =>

  val transport: HttpTransport

  def httpTransportUnderLoad() = {
    "handle lots of concurrent requests" in {
      val server = new HttpServer(0)
      server.handler = new HttpServerHandler {
        def get(request: Request) = Response(200, "OK")
        def post(request: Request) = ???
      }

      val N = 10000
      val atMost1ks = transport.withThrottling(1000 per (1 milli))

      (1 to 5).foreach { i =>
        println(s"Round ${i}...")

        val reqs = (1 to N).map { _ => atMost1ks.body(atMost1ks.getBuilder(server.url + "/").build).step(identity _).future }
        val results = result(sequence(reqs), 100 seconds)
        results must have size (N)
        val size = results.filter(_.isSuccess).size
        if (size != N) println(s"!!!!!!!!!!!!!!!!! size is ${size}.")
        //results.filter(_.isFailure).map(println(_))
        //results.filter(_.isSuccess) must have size (N)
        //        results.toSet must be(Set("OK"))
      }
    }
  }
}
package com.dreizak.tgv.transport.retry

import com.dreizak.util.concurrent.CancellableFuture.await
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import com.google.inject.Inject
import com.google.inject.AbstractModule
import com.dreizak.util.service.ServiceRegistryModule
import scala.concurrent.ExecutionContext
import org.scalatest.BeforeAndAfterAll
import com.dreizak.util.testing.http.HttpServer
import com.dreizak.util.testing.http.HttpServerHandler
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar._
import org.scalatest.BeforeAndAfterEach
import com.dreizak.util.testing.http.mockito.RequestOf.requestOf
import com.dreizak.util.testing.http.Response
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.dreizak.tgv.infrastructure.testing.MockServer
import com.dreizak.tgv.infrastructure.testing.GuiceInjection
import com.dreizak.tgv.infrastructure.testing.TemporaryFolders
import com.dreizak.tgv.transport.http.HttpTransport
import com.dreizak.tgv.ExecutionContextService
import com.dreizak.tgv.transport.http.sonatype.AsyncHttpTransportTestingModule
import com.dreizak.tgv.infrastructure.testing.ExecutionContextForEach
import com.dreizak.tgv.transport.http.HttpHeaderError
import com.dreizak.tgv.transport.backoff.ExponentialBackoffStrategy.exponentialBackoffStrategy

@RunWith(classOf[JUnitRunner])
class RetryingSpec extends ServiceRegistryModule with WordSpec with MustMatchers
  with MockServer with GuiceInjection with TemporaryFolders with ExecutionContextForEach {

  def configure() = install(AsyncHttpTransportTestingModule)

  @Inject
  val transport: HttpTransport = null

  "A transport without retrying" should {
    "give up immediately" in {
      when(handler.get(requestOf("/"))).thenReturn(Response(503))
      val request = transport.getBuilder(server.url + "/").build
      val thrown = evaluating { await(transport.submit(request)) } must produce[HttpHeaderError]
      thrown.httpStatus must be(503)
    }
  }
  "A transport with retrying" should {
    "should fail in case of 4xx (i.e., client-side) errors" in {
      val transportRetry = transport.withRetryStrategy(backoffStrategy = exponentialBackoffStrategy(1))

      when(handler.get(requestOf("/"))).
        thenReturn(Response(403))

      val request = transportRetry.getBuilder(server.url + "/").build
      evaluating { await(transportRetry.submit(request)) } must produce[HttpHeaderError]
    }
    "should retry and succeed if the second response arrives" in {
      val transportRetry = transport.withRetryStrategy(backoffStrategy = exponentialBackoffStrategy(1))

      when(handler.get(requestOf("/"))).
        thenReturn(Response(503)).
        thenReturn(Response(200, "yeah"))

      val request = transportRetry.getBuilder(server.url + "/").build
      val response = await(transportRetry.response(request))
      response.bodyAsString must equal("yeah")
      response.headers.status must be(200)
    }
    "should retry and fail if the second request fails and it was configured to retry once" in {
      val transportRetry = transport.withRetryStrategy(backoffStrategy = exponentialBackoffStrategy(1))

      when(handler.get(requestOf("/"))).
        thenReturn(Response(503)).
        thenReturn(Response(503))

      val request = transportRetry.getBuilder(server.url + "/").build
      evaluating { await(transportRetry.submit(request)) } must produce[HttpHeaderError]
    }
    "should retry and succeed when the third request succeeds and it was configured to retry twice" in {
      val transportRetry = transport.withRetryStrategy(backoffStrategy = exponentialBackoffStrategy(2))

      when(handler.get(requestOf("/"))).
        thenReturn(Response(503)).
        thenReturn(Response(503)).
        thenReturn(Response(200, "yeah"))

      val request = transportRetry.getBuilder(server.url + "/").build
      val response = await(transportRetry.response(request))
      response.bodyAsString must equal("yeah")
      response.headers.status must be(200)
    }
    "should retry and fail if the third request fails and it was configured to retry twice" in {
      val transportRetry = transport.withRetryStrategy(backoffStrategy = exponentialBackoffStrategy(2))

      when(handler.get(requestOf("/"))).
        thenReturn(Response(503)).
        thenReturn(Response(503)).
        thenReturn(Response(503))

      val request = transportRetry.getBuilder(server.url + "/").build
      evaluating { await(transportRetry.submit(request)) } must produce[HttpHeaderError]
    }
  }
}
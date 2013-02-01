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
import com.dreizak.util.testing.http.Response
import com.dreizak.util.testing.http.mockito.RequestOf.requestOf
import scala.concurrent.Await.result
import scala.concurrent.Future.sequence
import scala.concurrent.duration._
import com.google.inject.Inject
import com.dreizak.tgv.transport.http.sonatype.StreamingAsyncHttpClient

/**
 * Base test for `Transport`s.
 *
 * An implementation of [[com.dreizak.tgv.transport.Transport]] should be tested with at least the behavior tests that are
 * exposed (as methods, like `httpTransport`) in this class, see for example
 * [[com.dreizak.tgv.transport.http.sonatype.AsyncHttpTransportSpec]].
 */
trait HttpTransportBehaviors {
  this: WordSpec with MustMatchers with MockServer with ExecutionContextForEach =>

  val transport: HttpTransport

  def httpTransportRequests() = {
    "create a new request builder from a given request, with new parameters" in {
      val req = transport.getBuilder("http://www.foo.bar/?q=m").build
      val copy = req.builder.withQueryString(("p", "2")).build
      copy.httpRequest.getUrl() must equal("http://www.foo.bar/?q=m&p=2")
    }
    "create a new request builder from a given request, overwriting parameters" in {
      val req = transport.getBuilder("http://www.foo.bar/?w=m").build
      val copy = req.builder.withQueryString(("w", "m")).build
      copy.httpRequest.getUrl() must equal("http://www.foo.bar/?w=m")
    }
  }

  def httpTransport(maxSizeOfNonStreamingResponses: Long) = {
    "handle a simple GET request (not mocked)" in {
      val request = transport.getBuilder("http://www.wikipedia.com").build
      val response = await(transport.response(request))
      response.headers.status must be(200)
      response.bodyAsString.toLowerCase must include("wikipedia")
    }
    "hande a simple GET request with ZIP compression (not mocked)" in {
      val request = transport.getBuilder("https://www.googleapis.com/customsearch/v1?cx=abc&q=foo&start=1").
        withHeaders(("Accept-Encoding", "gzip"), ("User-Agent", "Google-HTTP-Java-Client/1.11.0-beta (gzip)")).
        build
      val thrown = evaluating { await(transport.body(request)) } must produce[HttpHeaderError]
      thrown.failingResponse.get.bodyAsString must include("reason")
    }
    "handle a simple GET request" in {
      when(handler.get(requestOf("/"))).thenReturn(Response(200, "yes"))
      val request = transport.getBuilder(server.url + "/").build
      await(transport.body(request)) must equal("yes")
    }
    "handle a simple GET request (3MB)" in {
      val payload = repeat("yes", 1000000)
      when(handler.get(requestOf("/"))).thenReturn(Response(200, payload))
      val request = transport.getBuilder(server.url + "/").build
      await(transport.body(request)) must equal(payload)
    }
    "fail the non-streaming API is used and the response is huge" in {
      val payload = repeat("f", maxSizeOfNonStreamingResponses.toInt + 1)
      when(handler.get(requestOf("/"))).thenReturn(Response(200, payload))
      val request = transport.getBuilder(server.url + "/").build
      evaluating { await(transport.body(request)) } must produce[HttpInMemoryResponseTooLarge]
    }
    "fail on non-2xx responses (404)" in {
      when(handler.get(requestOf("/"))).thenReturn(Response(404))
      val request = transport.getBuilder(server.url + "/").build
      val error = evaluating { await(transport.body(request)) must equal("yes") } must produce[HttpHeaderError]
      error.httpStatus must equal(404)
    }

    // TODO: check redirection (3xx)
    // TODO test when no http status code is provided
  }

  def httpTransportWithTransforms() = {
    def createHierarchy() = {
      val middle = transport.withTransform(transformUrl(_ + "m"))
      val top = middle.withTransform(transformUrl(_ + "t"))
      (transport, middle, top)
    }

    "apply no transforms per default" in {
      val (bottom, middle, top) = createHierarchy()

      when(handler.get(requestOf("/"))).thenReturn(Response(200, "OK"))
      val request = bottom.getBuilder(server.url + "/").build
      await(bottom.body(request)) must equal("OK")
    }
    "apply per-request transforms in the order of the chaining of the involved transports (1)" in {
      val (bottom, middle, top) = createHierarchy()

      when(handler.get(requestOf("/m"))).thenReturn(Response(200, "OK"))

      val request = middle.getBuilder(server.url + "/").build
      await(middle.body(request)) must equal("OK")
    }
    "apply per-request transforms in the order of the chaining of the involved transports (2)" in {
      val (bottom, middle, top) = createHierarchy()

      when(handler.get(requestOf("/tm"))).thenReturn(Response(200, "OK"))

      val request = top.getBuilder(server.url + "/").build
      await(top.body(request)) must equal("OK")
    }
  }
}
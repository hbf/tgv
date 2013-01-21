package com.dreizak.tgv.transport

import org.scalatest.FlatSpec
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import com.google.inject.Inject
import com.dreizak.tgv.ExecutionContextService
import nu.rinu.test.Response
import nu.rinu.test.mockito.RequestOf.requestOf
import org.mockito.Mockito.when
import com.dreizak.tgv.transport.http.HttpTransport
import com.dreizak.tgv.infrastructure.testing.MockServer
import com.dreizak.util.concurrent.CancellableFuture.await
import com.dreizak.tgv.infrastructure.testing.ExecutionContextForEach
import com.google.common.base.Strings.repeat
import com.dreizak.tgv.transport.http.HttpHeaderError
import com.dreizak.tgv.transport.http.sonatype.HttpInMemoryResponseTooLarge

/**
 * Base test for `Transport`s.
 *
 * An implementation of [[com.dreizak.tgv.transport.Transport]] should be tested with at least the behavior tests that are
 * exposed (as methods) in this class. You can use `httpTransport` to run all tests, see for example
 * [[com.dreizak.tgv.transport.http.sonatype.AsyncHttpTransportSpec]].
 */
trait HttpTransportBehaviors {
  this: WordSpec with MustMatchers with MockServer with ExecutionContextForEach =>

  val transport: HttpTransport

  def httpTransport(maxSizeOfNonStreamingResponses: Long) = {
    "handle a simple GET request (not mocked)" in {
      val request = transport.getBuilder("http://en.wikipedia.com").build
      val response = await(transport.response(request))
      response.headers.status must be(200)
      response.bodyAsString.toLowerCase must include("wikipedia")
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
}
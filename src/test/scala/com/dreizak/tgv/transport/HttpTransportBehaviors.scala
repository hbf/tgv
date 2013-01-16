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

  def httpTransport() = {
    "handle a simple GET request" in {
      when(handler.get(requestOf("/"))).thenReturn(Response(200, "yes"))
      val request = transport.getBuilder(server.url + "/").build
      await(transport.body(request)) must be("yes")
    }

    // TODO test when no http status code is provided
  }
}
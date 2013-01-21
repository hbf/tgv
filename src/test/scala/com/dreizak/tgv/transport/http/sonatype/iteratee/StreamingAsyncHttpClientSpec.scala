package com.dreizak.tgv.transport.http.sonatype.iteratee

import scala.Array.canBuildFrom
import scala.concurrent.Await.result
import org.junit.runner.RunWith
import org.mockito.Mockito.when
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.infrastructure.testing.{ ExecutionContextForEach, MockServer }
import com.dreizak.util.concurrent.CancellableFuture.await
import com.google.common.base.Charsets.UTF_8
import com.google.common.base.Strings.repeat
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig.Builder
import nu.rinu.test.Response
import nu.rinu.test.mockito.RequestOf.requestOf
import play.api.libs.iteratee.Iteratee
import org.scalatest.junit.JUnitRunner
import com.dreizak.tgv.transport.http.HttpHeaders
import com.dreizak.util.concurrent.CancellableFuture.await
import com.google.common.base.Strings.repeat
import com.dreizak.tgv.transport.http.sonatype.StreamingAsyncHttpClient
import com.dreizak.tgv.transport.http.sonatype.HttpInMemoryResponseTooLarge

@RunWith(classOf[JUnitRunner])
class StreamingAsyncHttpClientSpec extends WordSpec with MustMatchers
  with MockServer with ExecutionContextForEach {

  val client = new StreamingAsyncHttpClient(
    new AsyncHttpClient(new Builder().
      setAllowPoolingConnection(true).
      setFollowRedirects(true).
      setCompressionEnabled(true).
      build
    ))
  val payload = repeat("yes", 10000)

  "A streaming AsyncHttpClient" should {
    "stream its response" in {
      when(handler.get(requestOf("/"))).thenReturn(Response(200, payload))

      val request = client.nativeClient.prepareGet(server.url + "/").build()
      def consumer(headers: HttpHeaders): Iteratee[Array[Byte], String] =
        Iteratee.consume[Array[Byte]]().map(arr => new String(arr, UTF_8))
      val future = client.streamResponse(request, consumer)
      val iteratee = await(future)(timeout)
      result(iteratee.run, timeout) must equal(payload)
    }
    "abort when respones are too long (and it is not asked to stream)" in {
      when(handler.get(requestOf("/"))).thenReturn(Response(200, payload))
      val request = client.nativeClient.prepareGet(server.url + "/").build()
      await(client.response(request, payload.length())).bodyAsString must equal(payload)
      evaluating { await(client.response(request, payload.length() - 1)) } must produce[HttpInMemoryResponseTooLarge]
    }
  }
}
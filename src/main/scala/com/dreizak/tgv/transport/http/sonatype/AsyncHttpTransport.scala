package com.dreizak.tgv.transport.http.sonatype

import com.dreizak.tgv.SchedulingContext
import com.dreizak.tgv.transport.{AbortStrategy, Client}
import com.dreizak.tgv.transport.http.{HttpHeaders, HttpRequest, HttpRequestBuilder, HttpTransport}
import com.dreizak.tgv.transport.http.sonatype.iteratee.StreamingAsyncHttpClient
import com.dreizak.util.concurrent.CancellableFuture
import com.weiglewilczek.slf4s.Logging

/**
 * A [[com.dreizak.tgv.transport.Transport]] implementation for the HTTP protocol that uses
 * <a href='https://github.com/sonatype/async-http-client'>Sonatype's AsyncHttpClient</a>.
 *
 * Use the companion object to create instances of this class.
 *
 * TODO: you should use the companion object
 *
 * == Connection pool and `withLimit` ==
 * The underlying Sonatype `AsyncHttpClient` uses an internal connection pool, which for performance and
 * stability reasons <a href='http://sonatype.github.com/async-http-client/performances.html'>should be bounded</a>
 * via <a href='http://sonatype.github.com/async-http-client/apidocs/reference/com/ning/http/client/AsyncHttpClientConfig.Builder.html#setMaximumConnectionsPerHost(int)'>`setMaximumConnectionsPerHost`</a> and/or
 * <a href='http://sonatype.github.com/async-http-client/apidocs/reference/com/ning/http/client/AsyncHttpClientConfig.Builder.html#setMaximumConnectionsTotal(int)'>`setMaximumConnectionsTotal`</a>. It is
 * important that the limit you set there is at least as large as what you use in `withLimit`. If not,
 * the underlying Sonatype `AsyncHttpClient` will run out of connections and throw exceptions.
 *
 * == Notes on `withRetryStrategy` ==
 * You have two options to retry requests. On a low level, you can ask the Sonatype `AsynchHttpClient` itself
 * to retry requests; you do this by configuring the client using <a href='http://sonatype.github.com/async-http-client/apidocs/reference/com/ning/http/client/AsyncHttpClientConfig.Builder.html#setMaxRequestRetry(int)'>`setMaxRequestRetry`</a>.
 * Alternatively (and probably recommended), you can request the client <em>not</em> to retry (by calling `setMaxRequestRetry(0)`) and
 * use `Transport`'s `withRetryStrategy`. This has for example the advantage that when you have also enabled
 * throttling (using `withThrottling`), retry requests will again be throttled.
 */
class AsyncHttpTransport private[sonatype] (client: StreamingAsyncHttpClient,
                                            handler_ : Client[HttpRequest],
                                            abortStrategy: AbortStrategy[HttpHeaders]) extends HttpTransport {
  override val handler = handler_

  override protected def create(handler: Client[HttpRequest], abortStrategy: AbortStrategy[Headers]): Self =
    new AsyncHttpTransport(client, handler, abortStrategy)

  override def getBuilder(url: String) = new HttpRequestBuilder(this, client.nativeClient.prepareGet(url), url)
  override def postBuilder(url: String) = new HttpRequestBuilder(this, client.nativeClient.preparePost(url), url)

  def requestBuilder(r: HttpRequest): RequestBuilder = new HttpRequestBuilder(this, client.nativeClient.prepareRequest(r.httpRequest), r.httpRequest.getUrl)
}

object AsyncHttpTransport extends Logging {
  private[sonatype] def asyncHttpHandler(client: StreamingAsyncHttpClient, abortStrategy: AbortStrategy[HttpHeaders]) =
    new Client[HttpRequest] {
      def checkHeaders[R](consumer: Consumer[R])(headers: Headers): Processor[R] = {
        abortStrategy.shouldAbort(headers).map(t => throw t)
        consumer(headers)
      }

      def submit[R](r: HttpRequest, consumer: Consumer[R])(implicit context: SchedulingContext): CancellableFuture[Processor[R]] =
        client.streamResponse(r.httpRequest, checkHeaders(consumer))
      // FIXME: what is this again? document! 
      //          mapFailure(ex => ex match {
      //            case e: ExecutionException if e.getCause.isInstanceOf[CancelledException] => e.getCause
      //            case _ => ex
      //          })
    }

  def apply(client: StreamingAsyncHttpClient) = {
    val abortStrategy = AbortStrategy.accept2xxStatusOnly()
    new AsyncHttpTransport(client, asyncHttpHandler(client, abortStrategy), abortStrategy)
  }
}
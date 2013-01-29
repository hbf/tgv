package com.dreizak.tgv.transport.http.sonatype

import com.dreizak.tgv.SchedulingContext
import com.dreizak.tgv.transport.{ Client }
import com.dreizak.tgv.transport.http.{ HttpHeaders, HttpRequest, HttpRequestBuilder, HttpTransport, HttpMethod, Get, Post }
import com.dreizak.util.concurrent.CancellableFuture
import com.weiglewilczek.slf4s.Logging
import com.dreizak.tgv.transport.Transport
import com.ning.http.client.Response
import com.ning.http.client.PerRequestConfig
import com.ning.http.client.Realm.RealmBuilder
import com.ning.http.client.AsyncHandler
import com.ning.http.client.AsyncHandler.STATE
import com.ning.http.client.HttpResponseBodyPart
import com.ning.http.client.HttpResponseStatus
import com.ning.http.client.HttpResponseHeaders
import com.ning.http.client.AsyncCompletionHandler
import com.dreizak.tgv.transport.http.HttpHeaderError

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
                                            handler_ : Client[HttpRequest]) extends HttpTransport {
  override val handler = handler_

  override protected def create(handler: Client[HttpRequest]): Self = new AsyncHttpTransport(client, handler)

  override private[http] def nativeBuildRequest(builder: RequestBuilder) = {
    val nativeBuilder = builder.method match {
      case Get => client.nativeClient.prepareGet(builder.url)
      case Post => client.nativeClient.preparePost(builder.url)
    }
    builder.headers.foreach(header => header._2.
      foreach(value =>
        nativeBuilder.addHeader(header._1, value)
      ))
    for ((key, values) <- builder.queryString; value <- values) {
      nativeBuilder.addQueryParameter(key, value)
    }
    builder.shouldFollowRedirects.map(nativeBuilder.setFollowRedirects(_))
    builder.timeout.map { t: Int =>
      val config = new PerRequestConfig()
      config.setRequestTimeoutInMs(t)
      nativeBuilder.setPerRequestConfig(config)
    }
    builder.virtualHost.map { v => nativeBuilder.setVirtualHost(v) }
    builder.auth.map {
      case (username, password, scheme) =>
        nativeBuilder.setRealm(new RealmBuilder()
          .setScheme(scheme)
          .setPrincipal(username)
          .setPassword(password)
          .setUsePreemptiveAuth(true).
          build())
    }

    new HttpRequest(builder.transport, builder.backoffStrategy, builder.retryStrategy, builder, nativeBuilder.build())
  }

  override def getBuilder(url: String) = new HttpRequestBuilder(this).withUrl(url)
  override def postBuilder(url: String) = new HttpRequestBuilder(this).withUrl(url)
}

object AsyncHttpTransport extends Logging {
  private[sonatype] def asyncHttpHandler(client: StreamingAsyncHttpClient, maxSizeOfNonStreamingResponses: Long) =
    new Client[HttpRequest] {
      def submit(request: HttpRequest)(implicit context: SchedulingContext): CancellableFuture[HttpResponse] = {
        logger.debug(s"HTTP request $request.")
        client.response(request.httpRequest, maxSizeOfNonStreamingResponses).
          // FIXME: test cancellation (download big file and cancel immediately)
          //          mapFailure(ex => ex match {
          //            case e: ExecutionException if e.getCause.isInstanceOf[CancelledException] => e.getCause
          //            case _ => ex
          //          }).

          // Any message with a non-2xx status code is a error
          map {
            case response: HttpResponse =>
              val status = response.headers.status
              if (status < 200 || status >= 300)
                throw new HttpHeaderError(s"HTTP status ${status} indicates error.", status, request, Some(response))
              response
          }
      }
    }

  def apply(client: StreamingAsyncHttpClient, maxSizeOfNonStreamingResponses: Long) = new AsyncHttpTransport(client, asyncHttpHandler(client, maxSizeOfNonStreamingResponses))
}
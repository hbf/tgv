package com.dreizak.tgv.transport.http

import com.dreizak.tgv.transport.TransportRequest
import com.dreizak.tgv.transport.backoff.BackoffStrategy
import com.dreizak.tgv.transport.RetryStrategy
import com.dreizak.tgv.transport.AbortStrategy

/**
 * A request of a [[com.dreizak.tgv.transport.http.HttpTransport]].
 *
 * == Implementation note ==
 * Currently, this is hard-coded to use <a href='https://github.com/sonatype/async-http-client'>Sonatype's AsyncHttpClient</a>;
 * this should be changed to allow other implementations to be used.
 */
final class HttpRequest(val transport: HttpTransport,
                        val backoffStrategy: Option[BackoffStrategy],
                        val retryStrategy: Option[RetryStrategy],
                        val abortStrategy: Option[AbortStrategy[HttpHeaders]],
                        val httpRequest: com.ning.http.client.Request) extends TransportRequest {
  type Headers = HttpHeaders

  override def toString() = httpRequest.getRawUrl
}

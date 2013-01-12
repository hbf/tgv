package com.dreizak.tgv.transport.http

import com.dreizak.tgv.transport.TransportRequest
import com.dreizak.tgv.transport.http.sonatype.iteratee.ResponseHeaders
import com.dreizak.tgv.transport.retry.BackoffStrategy
import com.dreizak.tgv.transport.retry.RetryStrategy

/**
 * A request of a [[com.dreizak.tgv.transport.http.HttpTransport]].
 *
 * == Implementation note ==
 * Currently, this is hard-coded to use <a href='https://github.com/sonatype/async-http-client'>Sonatype's AsyncHttpClient</a>;
 * this should be changed to allow other implementations to be used.
 */
final class HttpRequest(val transport: HttpTransport,
                        val backoffStrategy: Option[BackoffStrategy],
                        val retryStrategy: Option[RetryStrategy[ResponseHeaders]],
                        val httpRequest: com.ning.http.client.Request) extends TransportRequest[ResponseHeaders] {

  override def toString() = httpRequest.getRawUrl
}

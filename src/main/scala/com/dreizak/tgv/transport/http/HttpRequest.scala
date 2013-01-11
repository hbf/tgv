package com.dreizak.deepar.infrastructure.transport.http

/**
 * A request of a [[com.dreizak.deepar.infrastructure.transport.http.HttpTransport]].
 *
 * == Implementation note ==
 * Currently, this is hard-coded to use <a href='https://github.com/sonatype/async-http-client'>Sonatype's AsyncHttpClient</a>;
 * this should be changed to allow other implementations to be used.
 */
final class HttpRequest(val transport: HttpTransport, val httpRequest: com.ning.http.client.Request) {

  override def toString() = httpRequest.getRawUrl
}

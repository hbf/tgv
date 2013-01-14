package com.dreizak.tgv.transport.http

import com.dreizak.tgv.transport.{ Transport, TransportDefinition, TransportHeaderError }

case class HttpHeaderError(msg: String, httpStatus: Int /* FIXME: , val failingResponse: HttpResponse */ ) extends RuntimeException(msg) with TransportHeaderError {
  // private val description = msg + " (" + httpStatus + "): " + failingResponse.getResponseBodyExcerpt(2048, "UTF-8") + "..."
  override def toString() = "HttpTransportHeaderFailureException fixme" // FIXME description
}

trait HttpTransportDefinition extends TransportDefinition {
  type Req = HttpRequest
  type Headers = HttpHeaders
  type HeaderFailureException = HttpHeaderError
  type RequestBuilder = HttpRequestBuilder
}

/**
 * A [[com.dreizak.tgv.transport.Transport]] for the HTTP protocol.
 *
 * == Creating requests ==
 * Use the `getBuilder`, `postBuilder`, etc. methods to start building a request. You can use `requestBuilder(r)` to create
 * a new builder based on the settings of a given request `r`.
 *
 * == Handling responses ==
 * The responses returned by `submit` are of type `HttpResponse`. You <em>must</em> at least one of the respone's methods whose names start with `getResponseBody`.
 * In case you call `getResponseBodyAsStream`, you <em>must</em> eventually close the returned stream.
 *
 * == Implementation note ==
 * Currently, we use <a href='https://github.com/sonatype/async-http-client'>Sonatype's AsyncHttpClient</a> library
 * to model `HttpRequest`s and `HttpResponse`s. This is not ideal as it does not allow us to easily switch to another HTTP client.
 * So in the future, we may introduce a proper abstraction for these two types, as well as for the builder type `RequestBuilder`.
 */
trait HttpTransport extends Transport[HttpTransportDefinition] {
  type Self = HttpTransport
  type RequestBuilder = HttpTransportDefinition#RequestBuilder

  def getBuilder(url: String): RequestBuilder
  def postBuilder(url: String): RequestBuilder
  // TODO: other HTTP methods

  def requestBuilder(r: Req): RequestBuilder
}

object HttpTransport {
  implicit def toRequest(builder: HttpRequestBuilder) = builder.build()
}
package com.dreizak.tgv.transport.http

import com.dreizak.tgv.transport.{ Transport, TransportHeaderError }
import com.dreizak.tgv.SchedulingContext
import com.dreizak.util.concurrent.CancellableFuture

case class HttpHeaderError(msg: String, httpStatus: Int /* FIXME: , val failingResponse: HttpResponse */ ) extends RuntimeException(msg) with TransportHeaderError {
  // private val description = msg + " (" + httpStatus + "): " + failingResponse.getResponseBodyExcerpt(2048, "UTF-8") + "..."
  override def toString() = "HttpTransportHeaderFailureException fixme" // FIXME description
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
trait HttpTransport extends Transport[HttpRequest] {
  type Self = HttpTransport
  type RequestBuilder = HttpRequestBuilder

  def getBuilder(url: String): RequestBuilder
  def postBuilder(url: String): RequestBuilder // TODO
  // TODO: other HTTP methods

  def requestBuilder(r: HttpRequest): RequestBuilder

  /**
   * Submits a request to the underlying client, accumulating the response in memory and returning
   * it as a string, together with the response headers, all wrapped in a future.
   *
   * If the response headers indicate any charset, the method will use it.
   */
  def response(request: HttpRequest)(implicit context: SchedulingContext): CancellableFuture[(Headers, String)] =
    submit(request).map { case (headers, body) => (headers, new String(body, headers.charset())) }

  /**
   * Submits a request to the underlying client, accumulating the response in memory and returning
   * it as a string, wrapped in a future.
   *
   * A short-hand for `response(request).map { _._2 }`.
   */
  def body(request: HttpRequest)(implicit context: SchedulingContext): CancellableFuture[String] =
    response(request).map { _._2 }
}

object HttpTransport {
  implicit def toRequest(builder: HttpRequestBuilder) = builder.build()
}
package com.dreizak.tgv.transport.http

import com.dreizak.tgv.transport.{ Transport, TransportHeaderError }
import com.dreizak.tgv.SchedulingContext
import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.tgv.transport.http.sonatype.HttpResponse

case class HttpHeaderError(msg: String, httpStatus: Int, request: HttpRequest, failingResponse: Option[HttpResponse] = None)
  extends RuntimeException(msg + " (response: " + failingResponse.map(_.bodyAsString) + ")") with TransportHeaderError

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

  private[http] def nativeBuildRequest(requestBuilder: RequestBuilder): HttpRequest

  def getBuilder(url: String): RequestBuilder
  def postBuilder(url: String): RequestBuilder // TODO
  // TODO: other HTTP methods

  /**
   * Submits a request to the underlying client, accumulating the response in memory and returning
   * it as a string, together with the response headers, all wrapped in a future.
   *
   * If the response headers indicate any charset, the method will use it.
   */
  def response(request: HttpRequest)(implicit context: SchedulingContext): CancellableFuture[HttpResponse] =
    submit(request).map { case (headers, body) => new HttpResponse(headers, body) }

  /**
   * Submits a request to the underlying client, accumulating the response in memory and returning
   * it as a string, wrapped in a future.
   *
   * A short-hand for `response(request).map { _._2 }`.
   */
  def body(request: HttpRequest)(implicit context: SchedulingContext): CancellableFuture[String] =
    response(request).map { _.bodyAsString }
}

object HttpTransport {
  implicit def toRequest[Req <: HttpRequest](builder: HttpRequestBuilder) = builder.build()
}
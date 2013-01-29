package com.dreizak.tgv.transport.http.transform

import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.tgv.transport.Client
import com.dreizak.tgv.transport.transform.Transform
import com.dreizak.tgv.transport.http.HttpRequest
import com.dreizak.tgv.SchedulingContext
import com.dreizak.tgv.transport.http.HttpHeaders
import com.weiglewilczek.slf4s.Logging

/**
 * Transforms the URL of a request.
 */
abstract class UrlTransform[Req <: HttpRequest] extends Transform[Req] {
  final override def apply(req: Req, client: Client[Req])(implicit context: SchedulingContext): CancellableFuture[(HttpHeaders, Array[Byte])] =
    client.submit(req.builder.withUrl(transformUrl(req.httpRequest.getUrl)).build.asInstanceOf[Req])

  def transformUrl(url: String): String
}

object UrlTransform {
  def transformUrl[Req <: HttpRequest](f: String => String) = new UrlTransform[Req] with Logging {
    override def transformUrl(url: String) = f(url)
  }
}
package com.dreizak.tgv.transport.http

import com.ning.http.util.AsyncHttpProviderUtils

/**
 * HTTP response headers.
 */
case class HttpHeaders(status: Int, headers: Map[String, Seq[String]]) {
  def header(name: String): Option[String] = headers.get(name).filter(_.size > 0).map(_.last)

  // Note: adapted from com.ning.http.client.providers.ResponseBase
  private lazy val charset_ = header("Content-Type").
    flatMap(t => Option(AsyncHttpProviderUtils.parseCharset(t))).
    getOrElse("ISO-8859-1")

  def charset(): String = charset_
}
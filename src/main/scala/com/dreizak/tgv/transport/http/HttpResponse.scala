package com.dreizak.tgv.transport.http

import java.nio.charset.Charset

/**
 * HTTP response holding both the headers and the response body in memory.
 *
 * Please refer to the streaming API of [[com.dreizak.tgv.transport.Transport]] for information on
 * handling large responses with constant memory.
 */
class HttpResponse(val headers: HttpHeaders, val body: Array[Byte]) extends (HttpHeaders, Array[Byte])(headers, body) {
  def bodyAsString(charset: Charset) = new String(body, charset)
  def bodyAsString() = new String(body, headers.charset())
}

object HttpResponse {
  def toHttpResponse(response: (HttpHeaders, Array[Byte])) = new HttpResponse(response._1, response._2)
}
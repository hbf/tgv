package com.dreizak.tgv.transport.http.sonatype

import com.dreizak.tgv.transport.http.HttpHeaders
import java.nio.charset.Charset
import com.google.common.base.Charsets

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
package com.dreizak.tgv.transport

import com.dreizak.tgv.transport.http.HttpHeaders
import com.dreizak.tgv.transport.http.HttpHeaderError

/**
 * Determines which headers of a `Transport` response cause an exception to be thrown.
 *
 * Implementations should throw instances of a suitable subclass of `TransportHeaderError`.
 *
 * @see [[com.dreizak.tgv.transport.Transport]]
 * @see [[com.dreizak.tgv.transport.TransportHeaderError]]
 */
trait AbortStrategy[Headers] {
  def shouldAbort(headers: Headers): Option[Exception]
}

object AbortStrategy {
  /**
   * A [[com.dreizak.tgv.transport.AbortStrategy]] that accepts all headers (i.e., never reports an error).
   */
  def acceptAllHeaders[Headers]() =
    new AbortStrategy[Headers] {
      def shouldAbort(headers: Headers) = None
      override def toString = "acceptAllHeaders"
    }

  /**
   * A [[com.dreizak.tgv.transport.AbortStrategy]] that throws an error for any HTTP status code
   * that is not 2xx (success).
   */
  def accept2xxStatusOnly() =
    new AbortStrategy[HttpHeaders] {
      def shouldAbort(h: HttpHeaders) =
        if (h.status >= 200 && h.status < 300) None
        else throw new HttpHeaderError("HTTP status ${h.status} indicates error.", h.status)
      override def toString = "accept2xxStatusOnly"
    }
}
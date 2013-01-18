package com.dreizak.tgv.transport

import scala.util.Try
import scala.util.Failure
import com.dreizak.util.concurrent.CancelledException
import scala.util.Success
import com.dreizak.tgv.transport.http.HttpHeaders
import scala.util.control.NonFatal
import com.dreizak.tgv.transport.http.HttpHeaderError

/**
 * Determines which exceptions will cause a `Transport` request to be retried.
 *
 * Implementations should ensure that `shouldRetry` returns false it is passed a `CancelledException` as its `cause` parameter.
 *
 * @see [[com.dreizak.tgv.transport.Transport]]
 */
trait RetryStrategy {
  def shouldRetry(cause: Throwable): Boolean
}

object RetryStrategy {
  /**
   * A [[com.dreizak.tgv.transport.RetryStrategy]] that never retries.
   */
  def dontRetry() =
    new RetryStrategy {
      def shouldRetry(cause: Throwable) = false
      override def toString = "dontRetry"
    }

  /**
   * A [[com.dreizak.tgv.transport.RetryStrategy]] that retries on non-fatal errors but <em>not</em>
   * on [[com.dreizak.tgv.transport.TransportHeaderError]].
   *
   * Notice that this class does not retry on `CancelledException`s.
   */
  def retryOnIoErrors() =
    new RetryStrategy {
      def shouldRetry(cause: Throwable) = cause match {
        case _: CancelledException => false
        case _: TransportHeaderError => false
        case NonFatal(_) => true
        case _ => false
      }
      override def toString = "retryOnIoErrors"
    }

  /**
   * A [[com.dreizak.tgv.transport.RetryStrategy]] that retries on non-fatal errors and
   * any [[com.dreizak.tgv.transport.TransportHeaderError]] except (most) 4xx (client error) status.
   *
   * We say most because some 4xx (client error) codes, as <a href='http://en.wikipedia.org/wiki/List_of_HTTP_status_codes'>listed
   * on Wikipedia</a>, do seem to have a chance of success on retry. Currently, the following 4xx codes <em>will</em>
   * be retried: 420, 429.
   */
  def retryAllBut4xx() =
    new RetryStrategy {
      def shouldRetry(cause: Throwable) = cause match {
        case _: CancelledException => false
        case HttpHeaderError(_, status, _) if status < 400 || status >= 500 || status == 420 || status == 429 => true
        case NonFatal(_) => true
        case _ => false
      }
      override def toString = "retryAllBut4xx"
    }
}
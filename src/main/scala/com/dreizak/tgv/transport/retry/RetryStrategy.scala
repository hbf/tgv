package com.dreizak.tgv.transport.retry

import com.dreizak.tgv.transport.TransportDefinition
import scala.util.Try

/**
 * Inspects the header of a `Transport` response to determine whether the request needs to be retried.
 *
 * @see [[com.dreizak.tgv.transport.Transport]]
 */
trait RetryStrategy[Headers] {
  def shouldRetry(headers: Headers): Boolean
}
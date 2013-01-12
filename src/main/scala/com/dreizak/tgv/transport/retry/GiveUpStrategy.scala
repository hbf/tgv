package com.dreizak.tgv.transport.retry

import scala.util.Try

import com.dreizak.tgv.transport.TransportDefinition

/**
 * A [[com.dreizak.tgv.transport.retry.BackoffStrategy]] that never retries.
 */
final class GiveUpStrategy extends BackoffStrategy {
  def escalate() = this
  val shouldRetry = false
  def delay = throw new IllegalStateException("Must not be called; shouldRetry returned false.")
  def attempt = throw new IllegalStateException("Must not be called; shouldRetry returned false.")
  override def toString() = "[not retrying]"
}
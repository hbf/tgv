package com.dreizak.tgv.credentials

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, DurationInt}

import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.util.concurrent.CancellableFuture.{failed, successful}
import com.dreizak.util.time.TimeProvider
import com.dreizak.util.time.TimeProvider.systemTimeProvider

/**
 * A [[com.dreizak.tgv.credentials.CredentialProvider]] that may temporarily expire.
 *
 * A typical example for this is a Google API Access Token for for example the Google Custom
 * Search API: such a token allows a certain number of accesses and will temporarily expire
 * until the next day when the quota is reached.
 *
 * @param credentials the credentials to provide through the `get` method
 */
abstract class TemporarilyExpiringCredential[C](private val credential: C)(implicit timeProvider: TimeProvider = systemTimeProvider()) extends CredentialProvider[C] {
  private var isExpired = false
  private var lastExpiredTime = 0L

  override def toString() = "TemporarilyExpiringCredential[" + credential + "/expired:" + isExpired + "]";

  override def get()(implicit executor: ExecutionContext): CancellableFuture[C] =
    synchronized {
      if (!isExpired || canProvideAgain(credential, lastExpiredTime)) {
        isExpired = false
        successful(credential)
      } else
        failed(new CredentialExpiredException("Credential expired (maybe just temporarily): " + credential))
    }

  override def expired(c: C): Unit = synchronized {
    if (c == credential) {
      lastExpiredTime = timeProvider.now()
      isExpired = true
    }
  }

  /**
   * Can an expired set `credentials` of credentials be provided again?
   *
   * A subclass must override this; the method will be called when the credentials managed by
   * this [[com.dreizak.tgv.credentials.TemporarilyExpiringCredential]] instance
   * have expired &mdash; because `expired` was called &mdash; and subsequently, the `get`
   * method is called.
   *
   * The method should return true iff `get` should return the credentials; otherwise get will
   * return `None`.
   *
   * @param credentials the credentials of this instance
   * @param last time (obtained from `System.currentTimeMillis`) the credentials were reported to not work
   */
  protected def canProvideAgain(credentials: C, lastExpiredTime: Long): Boolean
}

/**
 * Factory methods for [[com.dreizak.tgv.credentials.TemporarilyExpiringCredential]].
 */
object TemporarilyExpiringCredential {
  /**
   * Creates a [[com.dreizak.tgv.credentials.TemporarilyExpiringCredential]] that
   * retries expired credentials after the given amount of milliseconds.
   *
   * @param credentials the credentials to provide
   * @param retryDelayInMs the provider's `get` method will provide expired credentials again only after this many milliseconds have passed
   */
  def temporarilyExpiringCredential[C](credential: C, retryDelayInMs: Duration = 1 hour)(implicit timeProvider: TimeProvider = systemTimeProvider()) =
    new TemporarilyExpiringCredential[C](credential) {
      override def canProvideAgain(credential: C, lastExpiredTime: Long): Boolean = {
        timeProvider.now() - lastExpiredTime >= retryDelayInMs.toMillis
      }
    }
}
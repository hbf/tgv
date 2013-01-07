package com.dreizak.tgv.credentials

import scala.Array.{canBuildFrom, fallbackCanBuildFrom}
import scala.annotation.implicitNotFound
import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import com.dreizak.tgv.credentials.TemporarilyExpiringCredential.temporarilyExpiringCredential
import com.dreizak.util.concurrent.CancellableFuture

/**
 * A pool of [[com.dreizak.tgv.credentials.CredentialProvider]] .
 *
 * Notice that the credentials provided through `providers` will be switched in a round-robin
 * fashion: if the credentials from `provider(0)` expire, subsequent `get` requests will
 * use `provider(1)`; if `provider(1)` expires then subsequent `get` requests will use
 * `provider(2)` etc.
 *
 * A typical usage example is a collection of several different user accounts for a
 * certain service with the idea to use the first account as long as possible,
 * and switch to the next one in case the current account becomes unavailable.
 *
 * @param providers a non-empty list of credentials providers to use
 */
class CredentialsPool[C](providers: Seq[CredentialProvider[C]]) extends CredentialProvider[C] {
  private val size = providers.size

  require(size > 0)

  /**
   * The list `providers` rotated in such a way that the "current" provider is the
   * head of the list; we will rotate this list again by one element whenever the
   * current provider expires.
   *
   * Note: must only be accessed from within `synchronized`.
   */
  private var ps = Queue() ++ providers

  /**
   * Counts the number of times we rotated. This will only ever be incremented.
   *
   * Note: must only be accessed from within `synchronized`.
   */
  private var rotateCount: Int = 0

  private def rotate() = synchronized {
    val (head, queue) = ps.dequeue
    ps = queue.enqueue(head)
    rotateCount = rotateCount + 1
    //    println("rotated to " + ps)
  }

  private def get(intialRatateCount: Int)(implicit executor: ExecutionContext): CancellableFuture[C] = {
    val (head, count) = synchronized { (ps.head, rotateCount) }

    if (count - intialRatateCount < size)
      head.get.cancellableRecoverWith {
        case throwable =>
          rotate()
          get(intialRatateCount)
      }
    else head.get
  }

  override def get()(implicit executor: ExecutionContext): CancellableFuture[C] = get(synchronized { rotateCount - 1 })

  override def expired(c: C): Unit = synchronized {
    providers.foreach(_.expired(c))
  }
}

object CredentialsPool {
  /**
   * Takes a string containing one or more credentials, separated by a delimiter, and
   * creates a `CredentialsPool` containing a [[com.dreizak.tgv.credentials.TemporarilyExpiringCredential]]
   * for each set of credentials.
   *
   * @param delimitedString a list of credentials, separated by the delimeter `delimiter`; e.g., <tt>c1,c2,c3</tt>
   * @param delimiter the delimiter; e.g., <tt>,</tt>
   * @param retryDelayInMs the retry delay to configure the `TemporarilyExpiringCredentials` with (see documentation of the latter)
   */
  def poolWithTemporarilyExpiringCredentials(delimitedString: String, delimiter: Char = ',', retryDelay: Duration): CredentialsPool[String] =
    new CredentialsPool(delimitedString.split(delimiter).map(_.trim).filter(_.size > 0).map(credentials =>
      temporarilyExpiringCredential(credentials, retryDelay)))
}
package com.dreizak.tgv.credentials

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.util.Success

import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.util.concurrent.CancellableFuture.notCancellable

/**
 * A [[com.dreizak.tgv.credentials.CredentialProvider]] that provides a <em>session-id</em>, which it
 * obtains through a <em>login request</em>.
 *
 * A typical example for this is an external web service that after authentication provides a
 * session-id; the session-id (and not the username/password used for the authentication) will be
 * used in all subsequent requests and allows the user to access the service, possibly for a limited
 * amount of time. In this case, the <em>login request</em> is the authentication of the user with
 * the web service, which as a result provides a session-id.
 *
 * == Usage ==
 * A `SessionIdProvider` takes a <em>login credential</em> (usually a username-password-pair) of
 * type `L`. When its `get` method gets called for the first time, it will use the `login` method
 * (implemented by subclasses) that uses the login credentials to obtain a session-id (of type `S`).
 * From now on, the `get` method returns this session-id &mdash; until the client calls the `expired`
 * method; `expired` will cause the `login` method to be invoked again to obtain a fresh session-id.
 *
 * @param credentials the credentials used for the login request
 */
abstract class SessionIdProvider[S, L](private val loginCredential: CredentialProvider[L]) extends CredentialProvider[S] {
  /**
   * Note: When we invoke `login`, we will return the future it yields to all callers of `get`.
   * In this way, concurrent accesses to `get` will not result in tons of login requests.
   * As a consequence, however, we cannot return an ordinary cancellable future: if one client
   * cancelled it, it would be cancelled for all others, too. We do not support cancellation therefore.
   */
  private var outstanding: CancellableFuture[S] = null

  override def toString() = "SessionIdProvider[session=" + outstanding.future.value + "]";

  override def get()(implicit executor: ExecutionContext): CancellableFuture[S] = {
    if (outstanding == null) {
      synchronized {
        if (outstanding == null)
          outstanding = notCancellable(loginCredential.get().future.flatMap { l => login(l).future })
      }
    }
    outstanding
  }

  override def expired(c: S): Unit = synchronized {
    outstanding.future.value match {
      case Some(Success(sid)) if c == sid => outstanding = null
      case _ => // Not completed, or failure; so `c` cannot be equal to it.
    }
  }

  /**
   * Perfom a login request to obtain a session-id.
   *
   * A subclass must override this; the method will be called when a new session-id is required.
   *
   * @param loginCredential the login credential to use
   * @return a session-id
   */
  protected def login(loginCredential: L): CancellableFuture[S]
}
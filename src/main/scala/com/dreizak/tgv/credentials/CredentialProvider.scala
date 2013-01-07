package com.dreizak.tgv.credentials

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext

import com.dreizak.util.concurrent.CancellableFuture

/**
 * Provides a credential for accessing services.
 *
 * A [[com.dreizak.tgv.credentials.CredentialProvider]] encapsulate a possibly dynamically changing credential
 * for a (usually external) service.
 *
 * Often, external services provide <em>time-limited</em> or <em>quota-restricted</em> access to their service.
 * Thus, a credential needs to be renewed in an ad-hoc manner; or when the quota is reached then another
 * credential needs to be obtained/used until the quota for the original set is (possibly) restored.
 *
 * For example, the Google Custom Search API provides access through an API key that allows a fixed number
 * of search requests per day. Such an API key is a quota-restricted credential. The Dow Jones Factiva
 * Web Service requires a <em>session-id</em> to access the service, for a certain period of time. This
 * session-id is an example of a time-limited credential. In this particular case, you obtain a session-id
 * by calling the Factiva Session service with your username and password. The latter, i.e., the pair
 * `(username, password)`, is a <em>constant</em> credential for the Session service; "constant" in the
 * sense that in the context of the application, it is neither time- nor quota-restricted. (Constant
 * credentials can be obtained from the `constantCredentials` factory method of the companion object.)
 *
 * `CredentialProvider`'s are thread-safe.
 *
 * == Usage ==
 * You call the `get` method to obtain a credential; you should do this as shortly as possible
 * before actually accessing the service since another user might use the credential in the
 * meantime and exceed the quota, or the credential may expire.
 *
 * If a credential `c` returned by `get` is rejected by the service, you should call `expired(c)`
 * to notify the credential provider that `c` is (currently) not to be provided anymore in subsequent
 * `get` calls. The credential provider will then update its internal records and possibly make the
 * currently expired `c` available at a later time.
 */
trait CredentialProvider[C] {
  /**
   * Obtain a credential.
   *
   * You should call this method as shortly as possible before actually accessing the service.
   *
   * If no credential can be provided, the returned future will be a failure.
   */
  def get()(implicit executor: ExecutionContext): CancellableFuture[C]

  /**
   * Report that the given credential has not worked very recently.
   *
   * The underlying [[com.dreizak.tgv.credentials.CredentialProvider]] may disable the given
   * credential, either permanently or temporarily.
   *
   * If this credential provider so far has never provided `credential` (i.e., so far, no `get` has
   * yielded `credentials`), this method may have no effect.
   *
   * @param credential the credential that did not work
   */
  def expired(credential: C): Unit
}

object CredentialProvider {
  /**
   * Creates a [[com.dreizak.tgv.credentials.CredentialProvider]] that never expires.
   */
  def constantCredentials[C](credentials: C) = new CredentialProvider[C] {
    override def get()(implicit executor: ExecutionContext) = CancellableFuture.successful(credentials)
    override def expired(credentials: C) = {}
  }
}
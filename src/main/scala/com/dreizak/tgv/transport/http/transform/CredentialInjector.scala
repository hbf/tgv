package com.dreizak.tgv.transport.http.transform

import com.dreizak.tgv.credentials.CredentialProvider
import com.dreizak.tgv.transport.transform.Transform
import com.dreizak.tgv.transport.Client
import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.util.concurrent.CancelledException
import com.weiglewilczek.slf4s.Logging
import com.dreizak.tgv.transport.TransportRequest
import com.dreizak.tgv.SchedulingContext
import com.dreizak.tgv.transport.http.HttpRequest

// TODO document (and test): only expires the credential in case of ?!
abstract class CredentialInjector[C, Req <: TransportRequest](provider: CredentialProvider[C]) extends Transform[Req] with Logging {
  def apply(req: Req, client: Client[Req])(implicit context: SchedulingContext): CancellableFuture[(Headers, Array[Byte])] = {
    provider.get().cancellableFlatMap { credential =>

      // Embed credential (in URL or as header, for example)
      val transformedReq = embedCredential(req, credential)
      logger.trace(s"Transformed request ${req} to ${transformedReq}.")

      // Run the request ...
      val result = client.submit(transformedReq)

      // ... and report errors, if any
      result.future.onFailure {
        case e: CancelledException =>
        // case e: AccessException => // TODO/FIXME ?!
        case _ => provider.expired(credential)
      }

      result
    }
  }

  def embedCredential(req: Req, credential: C): Req
}

object CredentialInjector {
  def injectCredentialAsGetParam[C, Req <: HttpRequest](provider: CredentialProvider[C], paramName: String) =
    new CredentialInjector[C, Req](provider) {
      override def embedCredential(req: Req, credential: C) =
        req.transport.requestBuilder(req).withQueryString((paramName, credential.toString)).build.asInstanceOf[Req]
    }
}
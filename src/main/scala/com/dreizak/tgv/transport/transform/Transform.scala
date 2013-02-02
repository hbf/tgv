package com.dreizak.tgv.transport.transform

import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.tgv.transport.TransportRequest
import com.dreizak.tgv.transport.Client
import com.dreizak.tgv.SchedulingContext

/**
 * Transforms a `Request` submitted to a [[com.dreizak.tgv.transport.Transport]]
 * and/or its response (both in case of success of failure).
 *
 * TODO: this is likely to change when streaming gets implemented; to be on the safe (safer) side,
 * implementations <em>may</em> alter the request but should not rely on a response being available
 * (which would require reading it as a whole into memory and thus contradict streaming).
 * Use `InMemoryTransform` if you read non-constant amounts of the response into memory.
 */
trait Transform[Req <: TransportRequest] {
  type Headers = Req#Headers

  def apply(req: Req, client: Client[Req])(implicit context: SchedulingContext): CancellableFuture[(Headers, Array[Byte])]

  final def andThen(other: Transform[Req]): Transform[Req] = new Transform[Req] {
    def apply(req: Req, client: Client[Req])(implicit context: SchedulingContext): CancellableFuture[(Headers, Array[Byte])] =
      Transform.this.apply(req, new Client[Req] {
        def submit(request: Req)(implicit context: SchedulingContext): CancellableFuture[(Headers, Array[Byte])] = other.apply(request, client)
      })
  }
}

trait InMemoryTransform[Req <: TransportRequest] extends Transform[Req] {
  def apply(req: Req, client: Client[Req])(implicit context: SchedulingContext): CancellableFuture[(Headers, Array[Byte])]
}

object Transform {
  def identity[Req <: TransportRequest]() = new Transform[Req] {
    def apply(req: Req, client: Client[Req])(implicit context: SchedulingContext): CancellableFuture[(Headers, Array[Byte])] = client.submit(req)
  }
}
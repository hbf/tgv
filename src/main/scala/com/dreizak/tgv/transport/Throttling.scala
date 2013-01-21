package com.dreizak.tgv.transport

import com.dreizak.tgv.SchedulingContext
import com.dreizak.tgv.transport.throttle.{Rate, RateController}
import com.dreizak.tgv.transport.throttle.RateController.DefaultScheduler
import com.dreizak.tgv.transport.transform.Transform
import com.dreizak.util.concurrent.CancellableFuture

/**
 * A utility class that may be used to implement [[com.dreizak.tgv.transport.Transport]]s.
 *
 * Users should <em>not</em> use this class but call [[com.dreizak.tgv.transport.Transport]]'s
 * `withThrottling` method to obtain a `Transport` with throttling capabilities.
 *
 * == Implementation notes ==
 * The current implementation uses a [[com.dreizak.tgv.transport.throttle.RateController]]
 * to throttle requests. This has the advantage that the throttling guarantees are strict but the down-side
 * is that this throttler only supports a limited form of cancellation. More precisely, at any time,
 * at most `rate.numberOfTasks`-many requests will <em>not</em> be cancellable.
 */
private[transport] class Throttling[Req <: TransportRequest](rate: Rate, handler: Client[Req]) extends Transform[Req] {
  var throttler: RateController = null

  def apply(request: Req, client: Client[Req])(implicit context: SchedulingContext): CancellableFuture[(Headers, Array[Byte])] = {

    // Create the throttler if needed (we cannot do this at construction time because we have no scheduling context)
    if (throttler == null)
      synchronized {
        if (throttler == null)
          throttler = new RateController(rate, context, new DefaultScheduler()(context))
      }

    // The future that will be ready when we can execute our request
    val throttled = throttler.throttle()

    throttled.cancellableFlatMap(receipt => {
      handler.submit(request).
        // Note: we absolutely must call `receipt.completed()`, even in case of fatal exceptions.
        andThen {
          case r =>
            receipt.completed()
        }
    })
  }
}

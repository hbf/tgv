package com.dreizak.tgv.transport

import com.dreizak.tgv.SchedulingContext
import com.dreizak.tgv.transport.backoff.BackoffStrategy
import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.tgv.transport.http.sonatype.HttpResponse

/**
 * An exception thrown by a [[com.dreizak.tgv.transport.Transport]] in case the response headers
 * indicate an error.
 *
 * `TransportHeaderError`s are used in particular by retry strategies to decide whether the error
 * (in the case of HTTP: which HTTP status code) is such that the request should be retried.
 *
 * @see [[com.dreizak.tgv.transport.Transport]]
 */
trait TransportHeaderError extends Exception

/**
 * The base class for requests as submitted to a [[com.dreizak.tgv.transport.Transport]].
 *
 * A request may specify a per-request retry strategy and backoff strategy; in case one is not
 * provided, the transport's default strategy will be used, respectively.
 *
 * Refer to [[com.dreizak.tgv.transport.http.HttpRequest]] for a subclass of `TransportRequest`
 * for the HTTP protocol.
 */
trait TransportRequest {
  type Headers

  /**
   * The retry strategy to use of this request.
   *
   * If `retryStrategy` is `None`, the transport's default retry strategy will be used.
   */
  def retryStrategy: Option[RetryStrategy]

  /**
   * The backoff strategy to use for this request.
   *
   * If `backoffStrategy` is `None`, the transport's default backoff strategy will be used.
   */
  def backoffStrategy: Option[BackoffStrategy]
}

/**
 * A client &mdash; somewhere in a [[com.dreizak.tgv.transport.Transport]] pipeline.
 *
 * A `Client` takes a request and returns a response. A `Client` provides two ways of returning responses
 * to a caller, either by waiting for the whole response to arrive (accumulating it in memory) or
 * through <em>streaming</em>. (TODO: Streaming is not implemented yet)
 *
 * @see [[com.dreizak.tgv.transport.Transport]]
 */
trait Client[Req <: TransportRequest] {
  type Headers = Req#Headers
  type InMemoryResponse = (Headers, Array[Byte])

  /**
   * Submits the given request, reads the response (accumulating it in memory) and returns it wrapped in a future.
   *
   * The implementation may or may not have a limit in place that prevents reading responses that are longer
   * than a certain threshold. Refer to the streaming API (todo) for methods to handle large responses in
   * constant memory.
   *
   * TODO: introduce a new method for streaming and (possibly) implement this method here by using the streaming one
   *
   * @see [[com.dreizak.tgv.transport.Transport]]
   */
  def submit(request: Req)(implicit context: SchedulingContext): CancellableFuture[InMemoryResponse]
}

/**
 * Processes <em>requests</em> by sending them to a <em>client</em> and consuming their responses asynchronously.
 *
 * `Transport`s support <em>limiting the number of parallel connections</em>, <em>throttling</em>, <em>retrying</em>,
 * <em>transforming</em>, and in addition, <em>cancellation</em>. A `Transport` provides two ways of returning responses
 * to a caller (see trait [[com.dreizak.tgv.transport.Client]]), either by waiting for the whole response to arrive
 * (accumulating it in memory) or through <em>streaming</em>. (TODO: Streaming is not implemented yet)
 *
 * Throttling, retrying and transforming are implemented through "chaining". When you call a transport's
 * `withThrottling`, `withRetryStrategy`, or `withTransform` method, it returns a <em>new</em> transport
 * that provides the respective feature <em>on top</em> of the original transport. For example,
 *
 * {{{
 * val bare : Transport = ...
 * val throttled = bare.withThrottling(10 per (1 second))
 * val throttledAndRetrying = throttled.withRetryStrategy(exponentialRetryStrategy(maxRetries = 5))
 * }}}
 *
 * will create a <em>chain</em> of three transport instances, `bare`, `throttled`, and `throttledAndRetrying`.
 * Requests you submit to `bare` (using `bare.submit(req, consumer)`) will be processed without throttling.
 * However, calling `throttled.submit(req, consumer)` will impose the necessary delay (as to not break the
 * throttling limit of 10 messages per second) and eventually call the parent's `submit` method (the parent
 * being `bare` in this case). Similarly, calling `throttledAndRetrying.submit(req, consumer)` will forward the request
 * to `throttled`, which will impose the necessary delay and afterwards call `bare.submit` to process the
 * request. It will then return the response to `throttledAndRetrying`, which in case of a failure may
 * decide to resubmit the request to `throttled`.
 *
 * In general, when you `submit` a request to a transport, the transport will either process the request
 * itself, producing a response to return to the caller of `submit`, or, in case the transport was
 * obtained through one of the `with...` methods (like `withThrottling`, etc.), it will eventually
 * forward the request to its parent throttler, by calling the parent's `submit` method. Notice that in the
 * latter case, the request might be submitted to the parent any number of times (e.g., to retry the
 * request if necessary) and might change the request and/or its response along the way. In a chain
 * of transports, the one transport that processes the request itself (and does not have a parent to
 * which it `submit's the request) is called the <em>root transport</em>.
 *
 * Refer to the documentation of the concrete implementation to learn how to create `Request`s; some
 * implementations offer factories, others may provide dedicated methods that return request builders (or similar).
 *
 * `Transport`s are thread-safe.
 *
 * == Limiting the number of parallel requests ==
 *
 * The method `withLimit` returns a transport that allows at most a given number of
 * parallel requests. Without this, you may quickly hit the limits of your Internet connection
 * or run out of system ports (assuming you `submit` a huge number of requests to the transport).
 *
 * Notice that often, implementations require a "global limit" to be configured. If this global limit
 * is set to 100, say, and you create a `Transport` using `withLimit(1000)`, you might get errors.
 * If the implementation has such a hard global limit, ensure that you set it to a value at least as
 * large as the maximal value you are ever going to use in a call to `withLimit`.
 *
 * == Cancellation ==
 * `Transport`s support <em>optimistic cancellation</em>: you can cancel a `submit`'d request by
 * calling `cancel` on the future returned by the `submit` method. The cancellation is <em>optimisitc</em>
 * in the sense that if you cancel a request ''r'', even at a time where it has not yet been executed
 * (for example, because it is delayed by throttling), there is no guarantee that the request will not
 * be executed. In other words, cancellation is provided on a best-effort basis.
 *
 * == Transforms ==
 *
 * A request and/or its response can be transformed using [[com.dreizak.tgv.transport.transform.Transform]]s.
 * You use the `withTransform` method of a `Transport` to obtain a new `Transport` instance which, when its `submit`
 * method is invoked for a request `r`, say, will first transform the request, then call its parent transport's
 * `submit` to obtain a response and finally transforms the response. (TODO: The `Transform` trait is likely
 * to be change in order to support streaming. Therefore, implementations of it should not rely on a response
 * being available. That is, they may change the request and make zero, one, or more invocations of the
 * parent's `submit` method, but they should not rely on the response itself.)
 *
 * == Failures and retrying ==
 *
 * (TODO: When streaming is supported, retry may be requested &mdash; by means of throwing an exception &mdash;
 * during header inspection <em>before</em> the response has been entirely read. This has the advantage that
 * no resources are wasted reading the response in those cases when it is not needed anyway (this is the
 * reason why `HttpHeaderError` has an `Option` as its `failingResponse` field). However, while in the
 * non-streaming case (where the whole response is read first before the headers are inspected) we can
 * throw a `HttpHeaderError` on any non-2xx status code, we cannot do so in the streaming case &mdash;
 * precisely because this would not read the response body, which might be needed be the caller. So we
 * need to find a good API for all this.)
 *
 * When a request is processed, the root transport inspects the headers of the received response and
 * decides whether they signal an error. (In case of the HTTP protocol, any non-2xx status code is an
 * error; notice here that redirects (3xx status codes) will be followed by the client if it is
 * configured to do so, so these will not signal errors.) In case the headers indicate an error, the
 * root transport will throw a `HttpHeaderError` exception. Also, in case of any other errors, like
 * I/O problems, an exception will be thrown.
 *
 * If a parent transport has been obtained via `withRetryStrategy`, it will use its retry strategy to
 * inspect the exception that has been thrown and decide whether the request should be retried. If so,
 * a backoff strategy is used to control the delays between retries and the maximal number of attempted
 * retries.
 *
 * New transport instances do not retry. You can install a retry strategy using `withRetryStrategy`.
 *
 * == Implementation notes ==
 *
 * Two `Request`s must be equal iff they are the same instance. (FIXME: is this still required?)
 *
 * Cancellation:
 *
 *  - The current implementation uses a [[com.dreizak.tgv.transport.throttle.RateController]]
 *    to throttle requests. This has the advantage that the throttling guarantees are strict but the down-side
 *    is that this throttler only supports a limited form of cancellation. More precisely, at any time,
 *    at most `rate.numberOfTasks`-many requests will <em>not</em> be cancellable.
 *
 * Limiting:
 *
 *  - Limiting the number of parallel requests is currently realized through throttling.
 */
trait Transport[Req <: TransportRequest] extends Client[Req] {
  type Self <: Transport[Req]

  protected val handler: Client[Req]
  protected def create(handler: Client[Req]): Self

  /**
   * Submits a request to the underlying client.
   *
   * If the transport employs throttling (see `withThrottling`), the request will be passed to the underlying
   * client as soon as the rate control allows it. If the transport employs retrying (see `withRetrying`), the
   * request will be retried transparently behind the scenes (see also below). If the transport employs
   * transform (see `withTransform`) the transform(s) will transparently be applied.
   *
   * In case any error occurs (I/O-exception, etc.), the future returned by this method will be completed with the
   * error. This also holds for the case when `callback` or the consumer returned by it throw an exception. However,
   * if the transport employs retrying (see `withRetrying`), the retry strategy may decide, upon the inspection
   * of the thrown exception that the request will be retried.
   *
   * In case the response headers may indicate errors, as is the case with HTTP response headers, `callback`
   * may inspect the headers and throw an exception, possibly to be handled by a retry strategy in place.
   * Notice that if the callback does not throw an exception, it will be fed the response, whether or not
   * it contains the success or error response.
   *
   * @return a future holding the result of the computation or an exception in case of a failure
   */
  def submit(request: Req)(implicit context: SchedulingContext): CancellableFuture[InMemoryResponse] =
    handler.submit(request)

  /**
   * Creates a new `Transport` based on the current one that limits the number of parallel requests.
   *
   * Requests that are `submit`'d when already `maxNrOfParallelRequests` requests are running (have
   * been submitted and whose response has not yet been returned) are queued internally and will
   * be executed as soon as possible.
   *
   * @param maxNrOfParallelRequests the maximal number of concurrently executing requests
   */
  //def withLimit(maxNrOfParallelRequests: Int)(implicit context: SchedulingContext): Self //=
  //withThrottling(Rate(maxNrOfParallelRequests, 1 milli))

  /**
   * Creates a new `Transport` based on the current one that throttles request such that
   * the actual rate of requests sent using `submit` never exceeds the given threshold rate `rate`.
   * As a result, the futures returned by `submit` will be delayed accordingly. The delaying happens
   * asynchronously and does not involve blocking (except possibly for very short-term blocking
   * necessary for thread synchronization).
   *
   * @param rate the maximal rate that is allowed
   */
  //final def withThrottling(rate: Rate): Self =
  //  withTransform(new Throttling(rate, handler))

  /**
   * Creates a new `Transport` based on the current one that retries request according to the
   * given retry strategy.
   *
   * @param strategy retry strategy to use
   * @param softfailureDector how soft failures are detected
   */
  //def withRetryStrategy(strategy: RetryStrategy[Def], softfailureDector: RetryOracle[Def] = defaultRetryOracle): Self =
  //    withTransform(new Retrying(strategy, softfailureDector))

  /**
   * Creates a new `Transport` based on the current one that transforms requests and/or responses.
   *
   * @param transform the transform to apply
   */
  //  def withTransform(transform: Transform[Def]): Self =
  //    create(new Client[Def] {
  //      def submit[R](request: Req, consumer: Req#Headers => Consumer[R][R])(implicit context: SchedulingContext): CancellableFuture[Consumer[R]] =
  //        transform(request, consumer, handler)
  //    })
}

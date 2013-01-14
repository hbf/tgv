package com.dreizak.tgv.transport

import scala.concurrent.duration._
import com.dreizak.tgv.SchedulingContext
import com.dreizak.util.concurrent.CancellableFuture
import play.api.libs.iteratee.Iteratee
import com.dreizak.util.concurrent.Cancellable
import play.api.libs.iteratee.Enumerator
import com.dreizak.tgv.transport.backoff.BackoffStrategy

/**
 * An exception thrown by a [[com.dreizak.tgv.transport.Transport]] in case the response headers
 * indicate an error (FIXME by a `FailureDetector`).
 *
 * @see [[com.dreizak.tgv.transport.Transport]]
 * @see [[com.dreizak.tgv.transport.backoff.RetryOracle]] FIXME
 */
trait TransportHeaderError extends Exception {
  /**
   * The response that was classified as an error.
   */
  //  def failingResponse: Resp // FIXME
}

trait TransportRequest[Headers] {
  /**
   * The default retry strategy.
   *
   * If a request does not specify its own retry strategy (see FIXME), the strategy returned by this method
   * will be used.
   */
  def backoffStrategy: Option[BackoffStrategy]

  /**
   * The default retry strategy.
   *
   * If a request does not specify its own retry strategy (see FIXME), the strategy returned by this method
   * will be used.
   */
  def retryStrategy: Option[RetryStrategy]

  /**
   * The default abort strategy.
   *
   * If a request does not specify its own abort strategy (see FIXME), the strategy returned by this method
   * will be used.
   */
  def abortStrategy: Option[AbortStrategy[Headers]]
}

/**
 * Defines the request and response headers types of a [[com.dreizak.tgv.transport.Transport]] implementation,
 * including the type of the exceptions thrown when a header indicates an error.
 *
 * @see [[com.dreizak.tgv.transport.Transport]]
 */
trait TransportDefinition {
  type Req <: TransportRequest[Headers]
  type Headers
  type HeaderError <: TransportHeaderError

  /**
   * A `Processor[R]` is used to transform the incoming chunks into the response, which is of type `R`.
   */
  type Processor[R] = Iteratee[Array[Byte], R]

  /**
   * A `Consumer[R]` is the callback you provide when you `submit` a request to a `Client`. It first consumes
   * the headers and then produces a processor, which in turn converts the incoming data junks into the
   * response, which is of type `R`.
   */
  type Consumer[R] = Headers => Processor[R]
}

/**
 * A client &mdash; somewhere in a [[com.dreizak.tgv.transport.Transport]] pipeline.
 *
 * A `Client` takes a `Request` and streams back a response in a non-blocking way.
 *
 * @see [[com.dreizak.tgv.transport.Transport]]
 */
trait Client[Def <: TransportDefinition] {
  type Req = Def#Req
  type Headers = Def#Headers
  type Processor[R] = Def#Processor[R]
  type Consumer[R] = Def#Consumer[R]

  /**
   * Submits the given request.
   *
   * The response will eventually be fed to the given `consumer` (assuming no error occurs); no `EOF` will be fed to `consumer`.
   *
   * The returned future eventually holds the consumer iteratee in the state when it has been fed the last junk of data
   * of the response.
   *
   * In case of an error, the returned future will be completed with an exception. If the returned future is cancelled, it will
   * be completed with a `CancelledException` (if it is not already completed).
   *
   * Implementations may or may not support cancelling when the consumer has already received part of the response. It may
   * therefore happen that `consumer` has already been fed chunks of the response and then the request aborts (completing
   * the returned future with a `Cancelleded
   *
   * Notice that you will not have to "close" any resources
   */
  def submit[R](request: Req, consumer: Consumer[R])(implicit context: SchedulingContext): CancellableFuture[Processor[R]]
}

/**
 * Processes <em>requests</em> by sending them to a <em>client</em> and consuming their responses asynchronously.
 *
 * A `Transport` sends `Request`s to a <em>client</em>, which asynchronously produces a `Response` to be consumed
 * by the request's <em>consumer</em>. `Transport`s support <em>limiting the number of parallel connections</em>,
 * <em>throttling</em>, <em>retrying</em>, <em>transforming</em>, and in addition, <em>cancellation</em>.
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
 * In general, when you submit a request to a transport, using `submit`, the transport will either process the request
 * itself, producing a response to return to the caller of `submit`, or, in case the transport was
 * obtained through one of the `with...` methods (like `withThrottling`, etc.), it will eventually
 * forward the request to its parent throttler, by calling the parent's `submit` method. Notice that in the
 * latter case, the request might be submitted to the parent any number of times (e.g., to retry the
 * request if necessary) and might change the request and/or its response along the way.
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
 * `submit` to obtain a response and finally transforms the response.
 *
 * == Failures and retrying ==
 *
 * FIXME: retry can be requested during header inspection or at anytime before the request has finished
 * by throwing TODO
 *
 * A transport uses an underlying client to produce responses from requests. In doing so, two kinds of
 * errors can happen:
 *
 *  - <em>Hard failure:</em> the client cannot obtain a response at all for a given request. This may happen for example
 *    when there is no network connectivity, or when the transport makes request to a remote server
 *    that happens to be down.
 *
 *  - <em>Soft failure:</em> the client receives a response but the underlying transport protocol indicates an error.
 *    For example, [[com.dreizak.tgv.transport.http.HttpTransport]] uses the
 *    HTTP protocol, which indicates certain errors via HTTP Status Codes. The transport will
 *    decide which responses are failures and which not (and for HTTP, for instance, a response is
 *    a soft failure iff its status code is not a 2xx one).
 *
 * Soft failures result in an exception of type `HeaderFailureException` (or a subclass thereof), that is,
 * the future returned by the transport's `submit` method will be a `Failure(t)` where `t` is a
 * `HeaderFailureException`.
 *
 * A transport, `t`, say, obtained via `withRetryStrategy(s, o)` for a `RetryStrategy` `s` and a `RetryOracle` `o`,
 * behaves as follows:
 *
 *  1. When a request `r` is submitted via `t.submit(r)`, the transport `t` will `submit` the request
 *     to its parent.
 *  2. When the parent completes the request &mdash; successfully or with a failure &mdash; the TODO
 *
 * A transport uses a [[com.dreizak.tgv.transport.backoff.RetryStrategy]] and a
 * [[com.dreizak.tgv.transport.backoff.RetryOracle]] to realize retrying (see next section
 * for more details).
 *
 * New transport instances do not retry. You can install a retry strategy using `withRetryStrategy`.
 *
 * == Implementation notes ==
 *
 * Two `Request`s must be equal iff they are the same instance.
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
 *  - Limiting the number of parallel requests is currently realized through throttling. Implementations
 *    may override this.
 */
trait Transport[Def <: TransportDefinition] extends Client[Def] {
  type Self <: Transport[Def]
  type HeaderError = Def#HeaderError

  protected val handler: Client[Def]
  protected def create(handler: Client[Def]): Self

  //def defaultRetryOracle: RetryOracle[Def] // FIXME

  /**
   * The default retry strategy.
   *
   * If a request does not specify its own retry strategy (see FIXME), the strategy returned by this method
   * will be used.
   */
  def backoffStrategy: BackoffStrategy

  /**
   * The default retry tester.
   *
   * If a request does not specify its own retry tester (see FIXME), the tester returned by this method
   * will be used.
   */
  def retryStrategy: RetryStrategy

  /**
   * Submits a request to the underlying client.
   *
   * If the transport employs throttling (see `withThrottling`), the request will be passed to the underlying
   * client as soon as the rate control allows it.
   *
   * If the transport employs retrying (see `withRetrying`), the request will be retried transparently
   * behind the scenes.
   *
   * FIXME `withTransform`
   *
   * @return a future holding the result of the computation
   */
  override def submit[R](request: Req, consumer: Consumer[R])(implicit context: SchedulingContext): CancellableFuture[Processor[R]] =
    handler.submit(request, consumer)

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
  //      def submit[R](request: Req, consumer: Consumer[R])(implicit context: SchedulingContext): CancellableFuture[Processor[R]] =
  //        transform(request, consumer, handler)
  //    })
}
package com.dreizak.tgv

import scala.concurrent.duration._
import com.dreizak.tgv.transport.backoff.ExponentialBackoffStrategy.exponentialBackoffStrategy
import com.dreizak.tgv.transport.http.sonatype.{ AsyncHttpTransport, StreamingAsyncHttpClient }
import com.dreizak.tgv.transport.throttle.Rate.ToRate
import com.dreizak.util.concurrent.CancellableFuture.{ result, sequence }
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig.Builder

object SampleApp extends App {

  // Create the underlying HTTP client
  // (Note: currently, only Ning's AsyncHttpClient is supported.)
  val httpClient = new AsyncHttpClient(new Builder().
    setAllowPoolingConnection(true).
    setFollowRedirects(true).
    setCompressionEnabled(true).
    build)

  // Create threads to handle the request processing; all processing will be done
  // asynchronously, so the number of requests we can handle is not limited by the number
  // of threads configured here
  val numberOfThreads = Runtime.getRuntime().availableProcessors() * 2
  val executionService = new ExecutionContextService(numberOfThreads).start()
  implicit val executionContext = executionService.context()

  // Create a basic client (no retrying, no throttling, no transforms)
  val transport = AsyncHttpTransport(new StreamingAsyncHttpClient(httpClient))

  // Add throttling on top
  val throttled = transport.withThrottling(3 per (1 second))

  // Add retrying on top
  // Note: any requests on `retrying` will be throttled and, if needed, retried
  val retrying = throttled.withRetryStrategy(backoffStrategy = exponentialBackoffStrategy(maxRetries = 10))

  // Execute requests in parallel
  val words = List("Zurich", "Basel", "Paris", "London", "Washington", "Rome", "Palermo", "Nice", "Barcelona", "Vienna", "Berlin")
  val futures = words.map(w => retrying.body(retrying.getBuilder("http://en.wikipedia.org/wiki/" + w).build))

  // Wait until all responses have come in
  result(sequence(futures))(30 seconds)
  println("Done.")

  executionService.stop()
  httpClient.close()
  println("Shut down.")
}
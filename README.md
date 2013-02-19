## A simple asynchronous HTTP client for Scala

TGV is an experimental HTTP client for Scala that offers:

 - configurable **retrying** in case of errors;
 - strict **throttling** of requests, guaranteeing that the server receiving the requests will not see more than *n* concurrent requests per *t* seconds; and
 - custom **transformations**.
 
TGV is based on Ning's [Async HTTP Client](https://github.com/AsyncHttpClient/async-http-client) (currently version `1.8-SNAPSHOT`).

TGV is *experimental* code, a playground to explore non-blocking request/response IO in Scala. Currently, TGV reads the whole response from a HTTP request into memory before returning it. To make this more (resource-) efficient, there are plans to introduce a streaming API. For this, I am currently looking into Iteratees and similar techniques, like Pipes, Machines, or Conduits.

Even though TGV does come with unit tests, not all edge cases have been tested well enough yet. Use TGV in production with care.

## Example, please!

Here is a short [sample applications](https://github.com/hbf/tgv/blob/master/src/test/scala/com/dreizak/tgv/SampleApp.scala) that downloads the body of some Wikipedia pages using throttling:

```scala
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
```

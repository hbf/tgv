package com.dreizak.tgv.transport.http.sonatype.iteratee

import scala.collection.JavaConverters._
import scala.collection.immutable.TreeMap
import scala.concurrent.{ Future, Promise }
import com.google.inject.Inject
import com.ning.http.client.{ AsyncHttpClient, FluentCaseInsensitiveStringsMap, HttpResponseBodyPart, HttpResponseHeaders, HttpResponseStatus, Request }
import com.ning.http.client.AsyncHandler
import com.ning.http.client.AsyncHandler.STATE.{ CONTINUE, ABORT }
import play.api.libs.iteratee.{ Done, Error }
import play.api.libs.iteratee.{ Iteratee, Step }
import play.api.libs.iteratee.Input
import play.api.libs.iteratee.Input.{ El, Empty }
import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.tgv.transport.http.HttpHeaders

/**
 * A version of <a href='https://github.com/sonatype/async-http-client'>Sonatype's AsyncHttpClient</a> that allows streaming the response using iteratees.
 *
 * The code is loosely based on <a href='https://github.com/playframework/Play20/blob/master/framework/src/play/src/main/scala/play/api/libs/ws/WS.scala'>WS.scala</a>
 * from the Play Framework.
 *
 * @param client the `AsyncHttpClient` to use; this should be appropriately configured already
 */
class StreamingAsyncHttpClient @Inject() (val nativeClient: AsyncHttpClient) {
  import com.ning.http.client.Realm.{ AuthScheme, RealmBuilder }
  import javax.net.ssl.SSLContext

  /**
   * Asynchronously reads the response into memory and afterwards completes the returned future.
   *
   * TODO: Commented out because it's not clear to me whether end-users can call `getResponseBody...`
   * methods several times?!
   */
  //  def response(r: Request): Future[AHCResponse] = {
  //    var result = Promise[AHCResponse]()
  //    nativeClient.executeRequest(r,
  //      new AsyncCompletionHandler[AHCResponse]() {
  //        override def onCompleted(response: AHCResponse) = {
  //          result.success(response)
  //          response
  //        }
  //        override def onThrowable(t: Throwable) = {
  //          result.failure(t)
  //        }
  //      })
  //    result.future
  //  }

  /**
   * Passes the incoming chunks that make up the response to the iteratee `consumer`.
   *
   * This method will not feed `EOF` to `consumer`; you will have to do this yourself in
   * case the consumer iteratee relies on it. However, this method feeds an `Input.Empty`
   * at the end of the response.
   *
   * This method returns a cancellable future holding the final iteratee.
   *
   * @return the iteratee after having fed it the response
   */
  def streamResponse[A](r: Request, consumer: HttpHeaders => Iteratee[Array[Byte], A]): CancellableFuture[Iteratee[Array[Byte], A]] = {
    var doneOrError = false
    var statusCode = 0
    var iteratee: Iteratee[Array[Byte], A] = null

    val iterateeP = Promise[Iteratee[Array[Byte], A]]()
    val future = CancellableFuture.cancellable(iterateeP)

    val abortable = nativeClient.executeRequest(r, new AsyncHandler[Unit]() {
      import com.ning.http.client.AsyncHandler.STATE

      override def onStatusReceived(status: HttpResponseStatus) = {
        statusCode = status.getStatusCode()
        if (future.isCancelled) ABORT else CONTINUE
      }

      override def onHeadersReceived(h: HttpResponseHeaders) = {
        val headers = h.getHeaders()
        iteratee = consumer(HttpHeaders(statusCode, ningHeadersToMap(headers)))
        if (future.isCancelled) ABORT else CONTINUE
      }

      def feed(input: Input[Array[Byte]]) = {
        iteratee.pureFlatFold {
          case Step.Done(a, e) => {
            doneOrError = true
            val it = Done(a, e)
            iterateeP.trySuccess(it)
            it
          }

          case Step.Cont(k) => {
            k(input)
          }

          case Step.Error(e, input) => {
            doneOrError = true
            val it = Error(e, input)
            iterateeP.trySuccess(it)
            it
          }
        }
      }

      override def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
        if (!doneOrError) {
          iteratee = feed(El(bodyPart.getBodyPartBytes()))
          STATE.CONTINUE
        } else {
          iteratee = null
          STATE.ABORT
        }
      }

      override def onCompleted() = Option(iteratee).map(_ => iterateeP.trySuccess(feed(Empty)))
      override def onThrowable(t: Throwable) = iterateeP.tryFailure(t)
    })

    future.onCancellation(abortable.abort _)
  }

  private object CaseInsensitiveOrdered extends Ordering[String] {
    def compare(x: String, y: String): Int = x.compareToIgnoreCase(y)
  }

  private def ningHeadersToMap(headers: FluentCaseInsensitiveStringsMap) = {
    val res = mapAsScalaMapConverter(headers).asScala.map(e => e._1 -> e._2.asScala.toSeq).toMap
    // TODO: wrap the case insensitive ning map instead of creating a new one (unless perhaps immutabilty is important)
    TreeMap(res.toSeq: _*)(CaseInsensitiveOrdered)
  }
}
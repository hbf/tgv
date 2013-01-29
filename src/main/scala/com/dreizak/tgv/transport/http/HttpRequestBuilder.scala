package com.dreizak.tgv.transport.http

import scala.concurrent.duration._
import com.dreizak.tgv.transport.RetryStrategy
import com.dreizak.tgv.transport.backoff.BackoffStrategy
import com.ning.http.client.Realm.AuthScheme
import java.net.URI
import java.net.URLDecoder

trait SignatureCalculator {
  /**
   * Sign it.
   */
  def sign(request: HttpRequest)
}

sealed trait HttpMethod
case object Get extends HttpMethod
case object Post extends HttpMethod

/**
 * A request builder for requests to be submitted to a [[com.dreizak.tgv.transport.http.HttpTransport]].
 *
 * TODO: support passing a request body (both iteratee/enumerator- and stream-based)
 * TODO: cookie support
 *
 * == Implementation notes ==
 * When you call a `AsyncHttpClient#BoundRequestBuilder`'s `setUrl` method with a URL containing GET query
 * parameters, it automatically extracts the query parameters and internally calls `addQueryParameter`.
 *
 */
case class HttpRequestBuilder private[http] (private[http] val transport: HttpTransport,
                                             val method: HttpMethod = Get,
                                             val url: String = "",
                                             val headers: Map[String, Seq[String]] = Map(),
                                             val queryString: Map[String, Seq[String]] = Map(),
                                             val backoffStrategy: Option[BackoffStrategy] = None,
                                             val retryStrategy: Option[RetryStrategy] = None,
                                             val calc: Option[SignatureCalculator] = None,
                                             val auth: Option[(String, String, AuthScheme)] = None,
                                             val shouldFollowRedirects: Option[Boolean] = None,
                                             val timeout: Option[Int] = None,
                                             val virtualHost: Option[String] = None) {

  def sign(calc: SignatureCalculator): HttpRequestBuilder = copy(calc = Some(calc))

  def withMethod(method: HttpMethod) = copy(method = method)

  def withUrl(url: String): HttpRequestBuilder = {
    val uri = URI.create(url)
    val buildedUrl = new StringBuilder()

    if (uri.getScheme() != null) {
      buildedUrl.append(uri.getScheme())
      buildedUrl.append("://")
    }

    if (uri.getAuthority() != null) {
      buildedUrl.append(uri.getAuthority())
    }
    if (uri.getRawPath() != null) {
      buildedUrl.append(uri.getRawPath())
    } else {
      throw new IllegalArgumentException("Invalid url " + uri.toString())
    }

    var result = this
    if (uri.getRawQuery() != null && !uri.getRawQuery().equals("")) {
      val queries = uri.getRawQuery().split("&")
      queries.foreach(query => {
        val pos = query.indexOf("=")
        if (pos <= 0) {
          result = result.withQueryString((query, null))
        } else {
          result = result.withQueryString((URLDecoder.decode(query.substring(0, pos), "UTF-8"),
            URLDecoder.decode(query.substring(pos + 1), "UTF-8")))
        }
      })
    }
    result.copy(url = buildedUrl.toString)
  }

  def withAuth(username: String, password: String, scheme: AuthScheme): HttpRequestBuilder =
    copy(auth = Some((username, password, scheme)))

  def withHeaders(hdrs: (String, String)*): HttpRequestBuilder = {
    val headers = hdrs.foldLeft(this.headers)((m, hdr) =>
      if (m.contains(hdr._1)) m.updated(hdr._1, m(hdr._1) :+ hdr._2)
      else (m + (hdr._1 -> Seq(hdr._2)))
    )
    copy(headers = headers)
  }

  // TODO: document that this replaces whatever is currently stored under `key` while `addQueryString` adds
  // additional values
  def withQueryString(parameters: (String, String)*): HttpRequestBuilder =
    copy(queryString = parameters.foldLeft(queryString) {
      case (m, (k, v)) =>
        {
          val t = m + (k -> Seq(v))
          println(t)
          t
        }
    })

  def addQueryString(parameters: (String, String)*): HttpRequestBuilder =
    copy(queryString = parameters.foldLeft(queryString) {
      case (m, (k, v)) => m + (k -> (v +: m.get(k).getOrElse(Nil)))
    })

  def withFollowRedirects(follow: Boolean): HttpRequestBuilder =
    copy(shouldFollowRedirects = Some(follow))

  def followRedirects(): HttpRequestBuilder =
    copy(shouldFollowRedirects = Some(true))

  def withTimeout(timeout: Int): HttpRequestBuilder =
    copy(timeout = Some(timeout))

  def withTimeout(duration: FiniteDuration): HttpRequestBuilder =
    copy(timeout = Some(duration.toMillis.toInt))

  def withVirtualHost(vh: String): HttpRequestBuilder =
    copy(virtualHost = Some(vh))

  def withBackoffStrategy(strategy: BackoffStrategy): HttpRequestBuilder =
    copy(backoffStrategy = Some(strategy))

  def withRetryStrategy(strategy: RetryStrategy): HttpRequestBuilder =
    copy(retryStrategy = Some(strategy))

  def build() = transport.nativeBuildRequest(this)
}
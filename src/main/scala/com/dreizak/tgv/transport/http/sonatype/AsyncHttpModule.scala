package com.dreizak.tgv.transport.http.sonatype

import com.google.inject.{ AbstractModule, Provides, Singleton }
import com.google.inject.name.Named
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig.Builder
import java.util.concurrent.atomic.AtomicInteger
import net.codingwell.scalaguice.ScalaModule

/**
 * A module that provides bindings for <a href='https://github.com/sonatype/async-http-client'>Sonatype's AsyncHttpClient</a>.
 *
 * == Provided Guice bindings ==
 *
 * The module binds `AsyncHttpClient` to an application-global instance of <a href='http://sonatype.github.com/async-http-client/apidocs/reference/com/ning/http/client/AsyncHttpClient.html'>com.ning.http.client.AsyncHttpClient</a>.
 *
 * == Configuration ==
 *
 *  - `tgv.transport.http.allowPoolingConnection` (`Boolean`)
 */
class AsyncHttpModule extends AbstractModule with ScalaModule {
  override def configure = {}

  @Singleton
  @Provides
  def asyncHttpClient(
    @Named("tgv.transport.http.allowPoolingConnection") allowPoolingConnection: Boolean,
    @Named("tgv.transport.http.maxConnectionsTotal") maxConnectionsTotal: Int) =
    new AsyncHttpClient(new Builder().
      setAllowPoolingConnection(allowPoolingConnection).
      setFollowRedirects(true).
      // setMaximumConnectionsTotal(maxConnectionsTotal)  // #9
      build)
}
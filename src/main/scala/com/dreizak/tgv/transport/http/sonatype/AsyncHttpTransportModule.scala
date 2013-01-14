package com.dreizak.tgv.transport.http.sonatype

import java.util.concurrent.atomic.AtomicInteger
import com.dreizak.tgv.transport.http.HttpTransport
import com.dreizak.tgv.transport.http.sonatype.iteratee.StreamingAsyncHttpClient
import com.google.inject.{ AbstractModule, Provides }
import com.google.inject.Scopes.SINGLETON
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.google.inject.name.Names.named
import com.ning.http.client.AsyncHttpClient
import net.codingwell.scalaguice.ScalaModule

/**
 * A module that provides bindings for <a href='https://github.com/sonatype/async-http-client'>Sonatype's AsyncHttpClient</a>.
 *
 * == Provided Guice bindings ==
 *
 * The module binds `HttpTransport` to an application-global instance of [[com.dreizak.tgv.transport.http.sonatype.AsyncHttpTransport]].
 * Currently, this transport instance is configured to not support cancellation.
 *
 * == Dependencies ==
 *
 *  - Module `AsynchHttpModule`.
 *
 * == Configuration ==
 *
 *  - `tgv.transport.http.bufferLimit`
 */
class AsyncHttpTransportModule extends AbstractModule with ScalaModule {
  override def configure = {
    bind[StreamingAsyncHttpClient].in(SINGLETON)
  }

  @Singleton
  @Provides
  def asyncHttpTransport(client: StreamingAsyncHttpClient): HttpTransport = AsyncHttpTransport(client)
}
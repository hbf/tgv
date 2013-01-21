package com.dreizak.tgv.transport.http.sonatype

import com.google.inject.name.Names.named
import com.google.inject.AbstractModule

object AsyncHttpTransportTestingModule extends AbstractModule {
  val MaxSizeOfNonStreamingResponses = 10 * 1024 * 1024L

  override def configure() = {
    bindConstant().annotatedWith(named("tgv.transport.http.maxSizeOfNonStreamingResponses")).to(MaxSizeOfNonStreamingResponses)
    bindConstant().annotatedWith(named("tgv.transport.http.allowPoolingConnection")).to(true)
    bindConstant().annotatedWith(named("tgv.transport.http.maxConnectionsTotal")).to(300)
    install(new AsyncHttpModule())
    install(new AsyncHttpTransportModule())
  }
}
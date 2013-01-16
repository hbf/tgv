package com.dreizak.tgv.transport.http.sonatype

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import com.dreizak.util.service.ServiceRegistryModule
import com.dreizak.tgv.infrastructure.testing.GuiceInjection
import com.dreizak.tgv.infrastructure.testing.MockServer
import com.dreizak.tgv.infrastructure.testing.TemporaryFolders
import com.dreizak.tgv.transport.HttpTransportBehaviors
import com.google.inject.Inject
import com.dreizak.tgv.transport.http.HttpTransport
import com.dreizak.tgv.infrastructure.testing.ExecutionContextForEach

//import nu.rinu.test.HttpServer
//import nu.rinu.test.HttpServerHandler
//import org.mockito.Mockito.times
//import org.mockito.Mockito.verify
//import org.mockito.Mockito
//import org.mockito.Mockito.when
//import org.scalatest.mock.MockitoSugar._
//import org.scalatest.BeforeAndAfterEach
//import nu.rinu.test.mockito.RequestOf.requestOf
//import nu.rinu.test.Response
//import com.dreizak.tgv.testing.MockServer
//import com.dreizak.tgv.transport.http.HttpTransport._
//import com.dreizak.tgv.testing.TemporaryFolders

@RunWith(classOf[JUnitRunner])
class AsyncHttpTransportSpec extends ServiceRegistryModule with WordSpec with MustMatchers
  with GuiceInjection with MockServer with TemporaryFolders with ExecutionContextForEach with HttpTransportBehaviors {

  def configure() = install(new AsyncHttpTransportModule())

  @Inject
  override val transport: HttpTransport = null

  "A Sontatype AsyncHttpClient transport" should {
    "..." in { // example test not from `HttpTransportBehaviors`
      println(transport)
    }

    behave like httpTransport()
  }
}
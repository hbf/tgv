package com.dreizak.tgv.infrastructure.testing

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.mockito.Mockito.when
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers

import com.dreizak.tgv.ExecutionContextService
import com.dreizak.tgv.infrastructure.testing.TestingUtils.await
import com.dreizak.util.service.ServiceRegistryModule
import com.google.inject.Inject
// FIXME
//import com.dreizak.tgv.transport.http.HttpTransport
//import com.dreizak.tgv.transport.http.HttpTransport._

import nu.rinu.test.Response
import nu.rinu.test.mockito.RequestOf.requestOf

@RunWith(classOf[JUnitRunner])
class MockServerSpec extends ServiceRegistryModule with WordSpec with MustMatchers
  with GuiceInjection with MockServer with TemporaryFolders {

  override def configure() = {} //å install(new TestingModule())

  // FIXME
  //  @Inject
  //val transport: HttpTransport = null

  //@Inject
  var executor: ExecutionContextService = _

  "A mock server" should {
    "mock requests" in {
      //implicit val executorContext = executor.context

      when(handler.get(requestOf("/"))).thenReturn(Response(200, "yes"))
      //  val request = transport.getBuilder(server.url + "/")
      // FIXME // await(transport.submit(request).future).getResponseBody() must be("yes")
    }
    "mock several requests" in {
      //implicit val executorContext = executor.context

      when(handler.get(requestOf("/"))).thenReturn(Response(200, "yes"))

      // FIXME // await(transport.submit(transport.getBuilder(server.url + "/")).future).getResponseBody() must be("yes")
      // FIXME // await(transport.submit(transport.getBuilder(server.url + "/")).future).getResponseBody() must be("yes")
    }
  }
}
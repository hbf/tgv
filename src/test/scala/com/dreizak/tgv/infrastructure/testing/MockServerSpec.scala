package com.dreizak.tgv.infrastructure.testing

import org.junit.runner.RunWith
import org.mockito.Mockito.when
import org.scalatest.{ Finders, WordSpec }
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.ExecutionContextService
import com.dreizak.util.service.ServiceRegistryModule
import com.dreizak.util.testing.http.Response
import com.dreizak.util.testing.http.mockito.RequestOf.requestOf
import org.scalatest.junit.JUnitRunner

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
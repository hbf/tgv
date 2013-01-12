package com.dreizak.tgv.transport.http.sonatype
//
//import org.junit.runner.RunWith
//import org.scalatest.junit.JUnitRunner
//import org.scalatest.WordSpec
//import org.scalatest.matchers.MustMatchers
//import com.dreizak.tgv.testing.GuiceInjection
//import uk.me.lings.scalaguice.ScalaModule
//import com.dreizak.deepar.config.TestingModule
//import com.google.inject.Inject
//import com.google.inject.AbstractModule
//import com.dreizak.util.service.ServiceRegistryModule
//import com.dreizak.deepar.sources.query.Word
//import com.dreizak.deepar.sources.query.Phrase
//import scala.concurrent.ExecutionContext
//import com.dreizak.tgv.concurrency.ExecutionContextService
//import com.dreizak.tgv.testing.TestingUtils._
//import com.dreizak.tgv.transport.http.HttpTransport
//import org.scalatest.BeforeAndAfterAll
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
//
//@RunWith(classOf[JUnitRunner])
//class AsyncHttpTransportSpec extends ServiceRegistryModule with WordSpec with MustMatchers
//  with GuiceInjection with MockServer with TemporaryFolders {
//
//  def configure() = install(new TestingModule())
//
//  @Inject
//  val transport: HttpTransport = null
//
//  @Inject
//  var executor: ExecutionContextService = _
//
//  "A Sontatype AsyncHttpClient transport" should {
//    "..." in {
//      implicit val executorContext = executor.context
//
//      when(handler.get(requestOf("/"))).thenReturn(Response(200, "yes"))
//      val request = transport.getBuilder(server.url + "/")
//      // FIXME // await(transport.submit(request).future).getResponseBody() must be("yes")
//    }
//
//    // TODO test when no http status code is provided
//  }
//}
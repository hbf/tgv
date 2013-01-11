package com.dreizak.tgv.infrastructure.testing

import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach, Suite }
import org.scalatest.mock.MockitoSugar.mock
import nu.rinu.test.{ HttpServer, HttpServerHandler, Response }
import scala.io.Source
// FIXME
//import com.dreizak.tgv.transport.http.HttpTransportDefinition
//import com.dreizak.tgv.transport.Transport
//import com.dreizak.tgv.transport.http.transform.UrlTransform.transformUrl

/**
 * Mixin for ScalaTest `Suite`s that provides a HTTP server, running on a free local port,
 * that mocks HTTP requests.
 */
trait MockServer extends Suite with BeforeAndAfterEach with BeforeAndAfterAll {
  val port = 0
  var server: HttpServer = _
  var handler: HttpServerHandler = _

  override def afterEach() {
    server.stop()
    super.afterEach()
  }

  override def beforeEach() {
    super.beforeEach()
    server = new HttpServer(port)
    handler = mock[HttpServerHandler]
    server.handler = handler
  }

  /**
   * Reads the classpath-resource named `c-nameSuffix`.
   *
   * Uses Java's `getResourceAsStream` to read a resource from the class ''c'' of instance `base`
   * whose name is `c-nameSuffix`.
   */
  def responseFromResource(nameSuffix: String): Response = {
    val c = this.getClass
    val name = c.getSimpleName + "-" + nameSuffix
    val resource = c.getResource(name)
    if (resource == null) throw new IllegalStateException("Could not find file '" + name + "' next to class " + c + ".")
    Response(body = Source.fromURL(resource, "UTF-8").mkString)
  }

  // FIXME
  // val mockUrlTransfom = transformUrl[HttpTransportDefinition](_.replaceFirst("(http(s)?://[^/]+)", server.url))

  // FIXME
  // def mockTransport(transport: Transport[HttpTransportDefinition]) = transport.withTransform(mockUrlTransfom)
}
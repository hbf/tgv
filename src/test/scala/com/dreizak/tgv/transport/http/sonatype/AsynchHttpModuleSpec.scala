package com.dreizak.tgv.transport.http.sonatype

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import com.dreizak.util.service.ServiceRegistryModule
import com.google.inject.Inject
import com.google.inject.name.Names.named
import com.ning.http.client.AsyncHttpClient
import org.scalatest.junit.JUnitRunner
import com.dreizak.tgv.infrastructure.testing.GuiceInjection

/**
 * Simple check to verify the [[com.dreizak.tgv.transport.AsynchHttpModule]].
 *
 * Other tests ensure that specific requirements of the project are met, like
 * following redirects, accepting SSL certificates, detecting the encoding, etc.
 */
@RunWith(classOf[JUnitRunner])
class AsynchHttpModuleSpec extends ServiceRegistryModule with WordSpec with MustMatchers with GuiceInjection {

  override def configure() = {
    bind[Boolean].annotatedWith(named("tgv.transport.http.allowPoolingConnection")).toInstance(true)
    bind[Int].annotatedWith(named("tgv.transport.http.maxConnectionsTotal")).toInstance(300)
    install(new AsyncHttpModule())
  }

  @Inject
  val http: AsyncHttpClient = null

  "A http client" must {
    "provide a functional HTTP client" in {
      val response = http.executeRequest(http.prepareGet("http://en.wikipedia.com").build).get
      response.getStatusCode() must equal(200)
      response.getResponseBody("UTF-8").toLowerCase() must include("wikipedia")
    }
  }
}
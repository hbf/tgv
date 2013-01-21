package com.dreizak.tgv.transport.http.sonatype

import org.junit.runner.RunWith
import org.scalatest.{ Finders, WordSpec }
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.infrastructure.testing.{ ExecutionContextForEach, GuiceInjection, MockServer, TemporaryFolders }
import com.dreizak.tgv.transport.HttpTransportBehaviors
import com.dreizak.tgv.transport.http.HttpTransport
import com.dreizak.util.service.ServiceRegistryModule
import com.google.inject.Inject
import org.scalatest.junit.JUnitRunner
import com.google.inject.name.Names.named
import com.dreizak.tgv.transport.http.sonatype.AsyncHttpTransportTestingModule.MaxSizeOfNonStreamingResponses

@RunWith(classOf[JUnitRunner])
class AsyncHttpTransportSpec extends ServiceRegistryModule with WordSpec with MustMatchers
  with GuiceInjection with MockServer with TemporaryFolders with ExecutionContextForEach with HttpTransportBehaviors {

  def configure() = install(AsyncHttpTransportTestingModule)

  @Inject
  override val transport: HttpTransport = null

  "A Sontatype AsyncHttpClient transport" should {
    behave like httpTransport(MaxSizeOfNonStreamingResponses)
  }
}
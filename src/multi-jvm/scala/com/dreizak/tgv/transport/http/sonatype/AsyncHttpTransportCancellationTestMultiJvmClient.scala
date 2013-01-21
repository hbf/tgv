package com.dreizak.tgv.transport.http.sonatype

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.infrastructure.testing.{ ExecutionContextForEach, GuiceInjection, MultiJvmTestBarrier, TemporaryFolders }
import com.dreizak.tgv.transport.http.{ BaseLoadBehavior, HttpTransport }
import com.dreizak.util.service.ServiceRegistryModule
import com.google.inject.Inject
import org.scalatest.junit.JUnitRunner
import com.dreizak.tgv.transport.http.sonatype.AsyncHttpTransportLoadTestMultiJvmServer.Port

@RunWith(classOf[JUnitRunner])
class AsyncHttpTransportCancellationTestMultiJvmClient extends ServiceRegistryModule with WordSpec with MustMatchers
  with GuiceInjection with TemporaryFolders with ExecutionContextForEach with MultiJvmTestBarrier with BaseLoadBehavior {

  def configure() = install(AsyncHttpTransportTestingModule)

  @Inject
  val transport: HttpTransport = null

  "A Sontatype AsyncHttpClient transport" should {
    behave like httpTransportUnderCancellationLoadMultiJvm("AsyncHttpTransportCancellationTestMultiJvm", Port)
  }
}


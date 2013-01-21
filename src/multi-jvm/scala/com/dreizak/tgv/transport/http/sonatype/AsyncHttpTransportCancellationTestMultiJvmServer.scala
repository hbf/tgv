package com.dreizak.tgv.transport.http.sonatype

import org.junit.runner.RunWith
import org.mockito.Mockito.when
import org.scalatest.{ Finders, WordSpec }
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.infrastructure.testing.{ MockServer, MultiJvmTestBarrier }
import nu.rinu.test.Response
import nu.rinu.test.mockito.RequestOf.requestOf
import org.scalatest.junit.JUnitRunner
import com.google.common.base.Strings

@RunWith(classOf[JUnitRunner])
class AsyncHttpTransportCancellationTestMultiJvmServer extends WordSpec with MustMatchers
  with MultiJvmTestBarrier with MockServer {
  override val port = AsyncHttpTransportCancellationTestMultiJvmServer.Port

  "A Sontatype AsyncHttpClient transport" should {
    "handle load" in {
      enterBarrier(this, "AsyncHttpTransportCancellationTestMultiJvm")
      println("Server ready.")
      when(handler.get(requestOf("/long"))).thenReturn(Response(200, Strings.repeat("yes", 10000)))
      exitBarrier(this, "AsyncHttpTransportCancellationTestMultiJvm")
      println("Server about to stop...")
    }
  }
}

object AsyncHttpTransportCancellationTestMultiJvmServer {
  val Port = 11223
}


package com.dreizak.tgv.transport.http.sonatype

import org.junit.runner.RunWith
import org.mockito.Mockito.when
import org.scalatest.{ Finders, WordSpec }
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.infrastructure.testing.{ MockServer, MultiJvmTestBarrier }
import nu.rinu.test.Response
import nu.rinu.test.mockito.RequestOf.requestOf
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AsyncHttpTransportLoadTestMultiJvmServer extends WordSpec with MustMatchers
  with MultiJvmTestBarrier with MockServer {
  override val port = AsyncHttpTransportLoadTestMultiJvmServer.Port

  "A Sontatype AsyncHttpClient transport" should {
    "handle load" in {
      enterBarrier(this, "AsyncHttpTransportLoadTestMultiJvm")
      println("Server ready.")
      when(handler.get(requestOf("/"))).thenReturn(Response(200, "yes"))
      exitBarrier(this, "AsyncHttpTransportLoadTestMultiJvm")
      println("Server about to stop...")
    }
  }
}

object AsyncHttpTransportLoadTestMultiJvmServer {
  val Port = 11223
}


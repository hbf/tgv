package com.dreizak.tgv.transport.http.sonatype

import org.junit.runner.RunWith
import org.mockito.Mockito.when
import org.scalatest.{ Finders, WordSpec }
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.infrastructure.testing.{ MockServer, MultiJvmTestBarrier }
import com.dreizak.util.testing.http.Response
import com.dreizak.util.testing.http.mockito.RequestOf.requestOf
import org.scalatest.junit.JUnitRunner
import com.google.common.base.Strings
import com.dreizak.util.testing.http.HttpServer
import com.dreizak.util.testing.http.HttpServerHandler
import com.dreizak.util.testing.http.Request

// Note: we cannot use `MockServer` here as it remembers the history and would soon run out of memory.
@RunWith(classOf[JUnitRunner])
class AsyncHttpTransportCancellationTestMultiJvmServer extends WordSpec with MustMatchers
  with MultiJvmTestBarrier {
  "A Sontatype AsyncHttpClient transport" should {
    "handle load" in {
      val server = new HttpServer(AsyncHttpTransportCancellationTestMultiJvmServer.Port)
      server.handler = new HttpServerHandler {
        val payload = Strings.repeat("yes", 10000)
        def get(request: Request) = Response(200, payload)
        def post(request: Request) = ???
      }

      enterBarrier(this, "AsyncHttpTransportCancellationTestMultiJvm")
      println("Server ready.")
      exitBarrier(this, "AsyncHttpTransportCancellationTestMultiJvm")
      println("Server about to stop...")
    }
  }
}

object AsyncHttpTransportCancellationTestMultiJvmServer {
  val Port = 11223
}


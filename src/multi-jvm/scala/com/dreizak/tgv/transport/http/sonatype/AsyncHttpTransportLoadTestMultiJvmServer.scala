package com.dreizak.tgv.transport.http.sonatype

import org.junit.runner.RunWith
import org.mockito.Mockito.when
import org.scalatest.{ Finders, WordSpec }
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.infrastructure.testing.{ MockServer, MultiJvmTestBarrier }
import com.dreizak.util.testing.http.Response
import com.dreizak.util.testing.http.mockito.RequestOf.requestOf
import org.scalatest.junit.JUnitRunner
import com.dreizak.util.testing.http.HttpServer
import com.dreizak.util.testing.http.HttpServerHandler
import com.dreizak.util.testing.http.Request

// Note: we cannot use `MockServer` here as it remembers the history and would soon run out of memory.
@RunWith(classOf[JUnitRunner])
class AsyncHttpTransportLoadTestMultiJvmServer extends WordSpec with MustMatchers
  with MultiJvmTestBarrier {

  "A Sontatype AsyncHttpClient transport" should {
    "handle load" in {
      //waitForDebugging()
      val server = new HttpServer(AsyncHttpTransportLoadTestMultiJvmServer.Port)
      server.handler = new HttpServerHandler {
        def get(request: Request) = Response(200, "yes")
        def post(request: Request) = ???
      }

      enterBarrier(this, "AsyncHttpTransportLoadTestMultiJvm")
      println("Server ready.")
      exitBarrier(this, "AsyncHttpTransportLoadTestMultiJvm")
      println("Server about to stop...")
    }
  }
  def waitForDebugging() = {
    import java.lang.management.ManagementFactory
    println("Waiting to start... (pid " + ManagementFactory.getRuntimeMXBean().getName() + ")...")
    Thread.sleep(20000)
    println("Starting...")
  }
}

object AsyncHttpTransportLoadTestMultiJvmServer {
  val Port = 11223
}


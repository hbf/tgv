package com.dreizak.tgv.credentials

import java.lang.Thread.sleep
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import scala.concurrent.duration._
import com.dreizak.tgv.credentials.TemporarilyExpiringCredential.temporarilyExpiringCredential
import com.dreizak.util.concurrent.CancellableFuture
import com.dreizak.util.concurrent.CancellableFuture.{ cancellable, await }
import com.dreizak.tgv.credentials.CredentialProvider.constantCredentials
import scala.concurrent.Promise
import scala.concurrent.duration._
import com.dreizak.util.concurrent.DeterministicScheduling
import com.dreizak.tgv.infrastructure.testing.ExecutionContextForEach

@RunWith(classOf[JUnitRunner])
class SessionIdProviderSpec extends WordSpec with MustMatchers with ExecutionContextForEach {

  class TestProvider(login: String) extends SessionIdProvider[Int, String](constantCredentials(login)) {
    private var p: Promise[Int] = null

    def loginHappens(sid: Int) = {
      p.success(sid)
      println("Login yields session " + sid)
      p = null
    }

    override def login(l: String): CancellableFuture[Int] = {
      require(p == null)
      p = Promise[Int]()
      cancellable(p)
    }

    def loginPending: Boolean = p != null
  }

  "A session-id provider" must {
    "provide the given credentials" in {
      val c = new TestProvider("password")
      val f = c.get()
      sleep(500)
      f.future.isCompleted must be(false)
      c.loginHappens(31415)
      await(f) must equal(31415)
      c.loginPending must be(false)
      await(f)(0 millis) must equal(31415)
      c.loginPending must be(false)
    }
    "not make additional login requests when one is pending already" in {
      val c = new TestProvider("password")
      val f = c.get()
      sleep(500)
      f.future.isCompleted must be(false)

      // If `login` is called more than once, the following would throw
      val fs = (1 to 50).map(_ => c.get())

      // Specific to this implementation: all futures are the same
      fs.toSet must have size (1)

      c.loginHappens(31415)
      await(f) must equal(31415)
      fs.map(await(_)).toSet must equal(Set(31415))
      c.loginPending must be(false)
    }
  }
}
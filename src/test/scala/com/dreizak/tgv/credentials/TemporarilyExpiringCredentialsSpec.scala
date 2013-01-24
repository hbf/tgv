package com.dreizak.tgv.credentials

import java.lang.Thread.sleep
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import scala.concurrent.duration._
import com.dreizak.tgv.credentials.TemporarilyExpiringCredential.temporarilyExpiringCredential
import com.dreizak.util.concurrent.CancellableFuture.await
import com.dreizak.util.concurrent.DeterministicScheduling
import com.dreizak.tgv.infrastructure.testing.ExecutionContextForEach

@RunWith(classOf[JUnitRunner])
class TemporarilyExpiringCredentialsSpec extends WordSpec with MustMatchers with ExecutionContextForEach with DeterministicScheduling {
  "A temporarily expiring credentials provider" must {
    "provide the given credentials" in {
      val c = temporarilyExpiringCredential("key", 5 millis)(time)
      await(c.get) must equal("key")
      await(c.get) must equal("key")
      await(c.get) must equal("key")
    }

    "not provide the credentials for some period after expiration" in {
      val c = temporarilyExpiringCredential("key", 5 millis)(time)
      await(c.get) must equal("key")
      c.expired("key")
      evaluating { await(c.get) } must produce[CredentialExpiredException]
    }

    "provide expired credentials again after some time" in {
      val c = temporarilyExpiringCredential("key", 5 millis)(time)
      await(c.get) must equal("key")
      c.expired("key")
      evaluating { await(c.get) } must produce[CredentialExpiredException]
      time.tick(5)
      await(c.get) must equal("key")
      await(c.get) must equal("key")

      c.expired("key")
      evaluating { await(c.get) } must produce[CredentialExpiredException]
      time.tick(5)
      await(c.get) must equal("key")
      await(c.get) must equal("key")
    }
  }
}
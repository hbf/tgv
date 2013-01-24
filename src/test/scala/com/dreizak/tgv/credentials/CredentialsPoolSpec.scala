package com.dreizak.tgv.credentials

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
class CredentialsPoolSpec extends WordSpec with MustMatchers with ExecutionContextForEach with DeterministicScheduling {
  def newPool() = new CredentialsPool[Int](
    List(
      temporarilyExpiringCredential(1, 2 millis)(time),
      temporarilyExpiringCredential(2, 4 millis)(time),
      temporarilyExpiringCredential(3, 2 millis)(time)
    )
  )

  "A credentials pool" must {
    "provide the given credentials" in {
      val c = newPool()
      await(c.get) must equal(1)
      await(c.get) must equal(1)
      await(c.get) must equal(1)
    }

    "switch through the providers in a round-robin fashion" in {
      val c = newPool()
      await(c.get) must equal(1)
      c.expired(1)
      await(c.get) must equal(2)
      c.expired(2)
      await(c.get) must equal(3)
      c.expired(3)
      evaluating { await(c.get) } must produce[CredentialExpiredException]
    }

    "switch through the providers in a round-robin fashion even when providers become available again" in {
      val c = newPool()
      await(c.get) must equal(1)
      c.expired(1)
      await(c.get) must equal(2)
      c.expired(2)
      time.tick(4)
      // Here, 1 and 2 are available again, so we expect to get 2 again
      await(c.get) must equal(2)
      c.expired(2)
      c.expired(3)
      await(c.get) must equal(1)
    }

    "switch through the providers in a round-robin fashion even when providers become available again (2)" in {
      val c = newPool()
      await(c.get) must equal(1)
      c.expired(1)
      await(c.get) must equal(2)
      c.expired(2)
      time.tick(3)
      // Here, 1 (but not yet 2) is available again, so we expect to advance to 3
      await(c.get) must equal(3)
      c.expired(3)
      await(c.get) must equal(1)
    }

    "switch through the providers in a round-robin fashion even when providers become available again (3)" in {
      val c = newPool()
      await(c.get) must equal(1)
      c.expired(1)
      await(c.get) must equal(2)
      c.expired(2)
      time.tick(4)
      // Here, 1 and 2 are available again
      await(c.get) must equal(2)
      c.expired(2)
      c.expired(3)
      await(c.get) must equal(1)
    }

    "provide expired credentials again after some time" in {
      val c = newPool()
      await(c.get) must equal(1)
      c.expired(1)
      c.expired(2)
      c.expired(3)
      // The following get() will try 1, 2, and then 3, but will stop and fail at 3
      evaluating { await(c.get) } must produce[CredentialExpiredException]
      time.tick(3)
      // Here, 1 and 3 will be available again
      await(c.get) must equal(3)
      c.expired(3)
      await(c.get) must equal(1)
      c.expired(1)
      evaluating { await(c.get) } must produce[CredentialExpiredException]
      time.tick(3)
      await(c.get) must equal(3)
    }

    "provide expired credentials again after some time (2)" in {
      val c = newPool()
      await(c.get) must equal(1)
      c.expired(1)
      c.expired(2)
      c.expired(3)
      // The following get() will try 1, 2, and then 3, but will stop and fail at 3
      evaluating { await(c.get) } must produce[CredentialExpiredException]
      time.tick(3)
      // Here, 1 and 3 will be available again
      await(c.get) must equal(3)
      c.expired(3)
      await(c.get) must equal(1)
      c.expired(1)
      evaluating { await(c.get) } must produce[CredentialExpiredException]
      time.tick(1)
      await(c.get) must equal(2)
    }

    "provide expired credentials again after some time (3)" in {
      val c = newPool()
      await(c.get) must equal(1)
      c.expired(1)
      c.expired(2)
      c.expired(3)
      // The following get() will try 1, 2, and then 3, but will stop and fail at 3
      evaluating { await(c.get) } must produce[CredentialExpiredException]
      time.tick(3)
      // Here, 1 and 3 will be available again
      await(c.get) must equal(3)
      c.expired(3)
      await(c.get) must equal(1)
      time.tick(4)
      c.expired(1)
      await(c.get) must equal(2)
    }
  }
}
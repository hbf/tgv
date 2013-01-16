package com.dreizak.util.concurrent

import scala.concurrent.{ Future, Promise }
import scala.concurrent.duration.DurationInt
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import com.dreizak.tgv.infrastructure.testing.ExecutionContextForEach
import com.dreizak.tgv.infrastructure.testing.TestingUtils.await
import com.dreizak.util.concurrent.CancellableFuture.{ cancellable, delay }
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CancellableFutureSpec extends WordSpec with MustMatchers with ExecutionContextForEach {
  "A cancellable future" must {
    "be able to complete normally" in {
      val p = Promise[String]()
      val f = cancellable(p)
      p.isCompleted must be(false)
      p.success("Hi, Huck!")
      p.isCompleted must be(true)
      await(f.future) must equal("Hi, Huck!")
    }
    "be able to fail like a normal future" in {
      val p = Promise[String]()
      val f = cancellable(p)
      val failure = new IllegalStateException("oops")
      p.isCompleted must be(false)
      p.failure(failure)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[IllegalStateException]
      thrown must equal(failure)
    }
    "return a cancelled execption in case it get cancelled with a CancelledException" in {
      val p = Promise[String]()
      val f = cancellable(p)
      p.isCompleted must be(false)
      val reason = new CancelledException("oops")
      f.cancel(reason)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[CancelledException]
      thrown must equal(reason)
    }
    "return a wrapped cancelled execption in case it get cancelled with something other than a CancelledException" in {
      val p = Promise[String]()
      val f = cancellable(p)
      p.isCompleted must be(false)
      val reason = new IllegalStateException("oops")
      f.cancel(reason)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[CancelledException]
      thrown.getCause() must be(reason)
    }

    "(through map) be able to complete normally" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val f = f1 map { _.length }
      p.isCompleted must be(false)
      p.success("Hi, Huck!")
      p.isCompleted must be(true)
      await(f.future) must equal(9)
    }
    "(through map) be able to fail like a normal future" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val f = f1 map { _.length }
      val failure = new IllegalStateException("oops")
      p.isCompleted must be(false)
      p.failure(failure)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[IllegalStateException]
      thrown must equal(failure)
    }
    "(through map) return a cancelled execption in case it get cancelled with a CancelledException" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val f = f1 map { _.length }
      p.isCompleted must be(false)
      val reason = new CancelledException("oops")
      f.cancel(reason)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[CancelledException]
      thrown must equal(reason)
    }
    "(through map) return a wrapped cancelled execption in case it get cancelled with something other than a CancelledException" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val f = f1 map { _.length }
      p.isCompleted must be(false)
      val reason = new IllegalStateException("oops")
      f.cancel(reason)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[CancelledException]
      thrown.getCause() must be(reason)
    }

    "(through map) cancel when an intermediate future cancels" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val f = f1 map { _.length }
      p.isCompleted must be(false)
      val reason = new CancelledException("oops")
      f1.cancel(reason)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[CancelledException]
      thrown must equal(reason)
    }

    "(through flatMap) be able to complete normally" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val p2 = Promise[Int]
      val f = f1 flatMap { (str: String) => Future(str.length) }
      p.isCompleted must be(false)
      p.success("Hi, Huck!")
      p.isCompleted must be(true)
      await(f.future) must equal(9)
    }
    "(through flatMap) be able to fail like a normal future" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val p2 = Promise[Int]
      val f = f1 flatMap { (str: String) => Future(str.length) }
      val failure = new IllegalStateException("oops")
      p.isCompleted must be(false)
      p.failure(failure)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[IllegalStateException]
      thrown must equal(failure)
    }
    "(through flatMap) return a cancelled execption in case it get cancelled with a CancelledException" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val p2 = Promise[Int]
      val f = f1 flatMap { (str: String) => Future(str.length) }
      p.isCompleted must be(false)
      val reason = new CancelledException("oops")
      f.cancel(reason)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[CancelledException]
      thrown must equal(reason)
    }
    "(through flatMap) return a wrapped cancelled execption in case it get cancelled with something other than a CancelledException" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val p2 = Promise[Int]
      val f = f1 flatMap { (str: String) => Future(str.length) }
      p.isCompleted must be(false)
      val reason = new IllegalStateException("oops")
      f.cancel(reason)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[CancelledException]
      thrown.getCause() must be(reason)
    }

    "(through flatMap) cancel when an intermediate future cancels" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val p2 = Promise[Int]
      val f = f1 flatMap { (str: String) => Future(str.length) }
      p.isCompleted must be(false)
      val reason = new CancelledException("oops")
      f1.cancel(reason)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f.future) } must produce[CancelledException]
      thrown must equal(reason)
    }

    "work in combination with non-cancellable futures" in {
      val p = Promise[String]()
      val f1 = cancellable(p)
      val f = f1.future map { _.length }
      p.isCompleted must be(false)
      val reason = new CancelledException("oops")
      f1.cancel(reason)
      p.isCompleted must be(true)
      val thrown = evaluating { await(f) } must produce[CancelledException]
      thrown must equal(reason)
    }

    "not cancel an intermediate ordinary future (in flatMap)" in {
      val f1 = TestFuture("foo")
      val f2 = TestFuture(3)
      val f = f1.cancellableFuture flatMap { (str: String) => f2.future }
      f1.completed must be(false)
      f2.completed must be(false)
      f.future.isCompleted must be(false)

      f1.succeed()
      f1.completed must be(true)
      f2.completed must be(false)
      f.future.isCompleted must be(false)

      val reason = new CancelledException("oops")
      f.cancel(reason)
      f.isCancelled() must be(true)
      // Note: f1 need not be cancelled (in the current implementatino it is not)!.
      f2.cancellableFuture.isCancelled() must be(false) // Compare with next test.
    }
    "cancel an intermediate cancellable future (in flatMap)" in {
      val f1 = TestFuture("foo")
      val f2 = TestFuture(3)
      val f = f1.cancellableFuture cancellableFlatMap { (str: String) => f2.cancellableFuture }
      f1.completed must be(false)
      f2.completed must be(false)
      f.future.isCompleted must be(false)

      f1.succeed()
      f1.completed must be(true)
      f2.completed must be(false)
      f.future.isCompleted must be(false)

      val reason = new CancelledException("oops")
      f.cancel(reason)
      evaluating { await(f2.future, 10 seconds) } must produce[CancelledException]
      f.isCancelled() must be(true)
      // Note: f1 need not be cancelled (in the current implementatino it is not)!.
      f2.cancellableFuture.isCancelled() must be(true) // Compare with previous test.
    }

    "not cancel an intermediate ordinary future (in recoverWith)" in {
      val f1 = TestFuture("foo")
      val f2 = TestFuture("bar")
      val pf: PartialFunction[Throwable, Future[String]] = { case e => f2.future }
      val f = f1.cancellableFuture recoverWith pf
      f1.completed must be(false)
      f2.completed must be(false)
      f.future.isCompleted must be(false)

      f1.fail()
      f1.completed must be(true)
      f2.completed must be(false)
      f.future.isCompleted must be(false)

      val reason = new CancelledException("oops")
      f.cancel(reason)
      f.isCancelled() must be(true)
      // Note: f1 need not be cancelled (in the current implementatino it is not)!.
      f2.cancellableFuture.isCancelled() must be(false) // Compare with next test.
    }
    "cancel an intermediate cancellable future (in recoverWith)" in {
      val f1 = TestFuture("foo")
      val f2 = TestFuture("bar")
      val pf: PartialFunction[Throwable, CancellableFuture[String]] = { case e => f2.cancellableFuture }
      val f = f1.cancellableFuture recoverWith pf
      f1.completed must be(false)
      f2.completed must be(false)
      f.future.isCompleted must be(false)

      f1.fail()
      f1.completed must be(true)
      f2.completed must be(false)
      f.future.isCompleted must be(false)

      val reason = new CancelledException("oops")
      f.cancel(reason)
      evaluating { await(f2.future, 10 seconds) } must produce[CancelledException]
      f.isCancelled() must be(true)
      // Note: f1 need not be cancelled (in the current implementatino it is not)!.
      f2.cancellableFuture.isCancelled() must be(true) // Compare with previous test.
    }

    "cancel a delayed cancellable future" in {
      val p1 = Promise[Unit]()
      val p2 = Promise[Unit]()
      val f1 = delay(500 millis).map { x => p1.success(()) }
      val f2 = delay(750 millis).map { x => p2.success(()); "bar" }
      val reason = new CancelledException("oops")
      f1.cancel(reason)
      evaluating { await(f1.future, 10 seconds) } must produce[CancelledException]
      await(f2.future, 10 seconds) must equal("bar")
      f1.isCancelled() must be(true)
      f2.isCancelled() must be(false)
      p1.isCompleted must be(false)
      p2.isCompleted must be(true)
    }
  }

  case class TestFuture[T](value: T) {
    private val promise = Promise[T]()
    val cancellableFuture = cancellable(promise)
    val future = cancellableFuture.future
    def succeed() = promise.success(value)
    def fail() = promise.failure(new IllegalStateException("nah..."))
    def completed = promise.isCompleted
  }
}
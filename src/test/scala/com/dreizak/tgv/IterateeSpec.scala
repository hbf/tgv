package com.dreizak.tgv

import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.junit.JUnitRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import com.google.common.io.ByteStreams
import com.google.common.io.Closeables
import resource._
import java.io.InputStream
import java.io.OutputStream
import play.api.libs.iteratee.Iteratee
import play.api.libs.iteratee.Iteratee._
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Enumerator._
import scala.concurrent.Await.result
import scala.concurrent.duration._
import scala.concurrent.Future
import play.api.libs.iteratee.Enumeratee
import com.google.common.base.Charsets.UTF_8
import java.io.FileOutputStream
import java.io.File
import play.api.libs.iteratee.Step
import play.api.libs.iteratee.Step._

@RunWith(classOf[JUnitRunner])
class IterateeSpec extends WordSpec with MustMatchers {
  val timeout = 30 seconds

  "Iteratees" must {
    "test1" in {
      val names = Enumerator("Guillaume", "Sadek", "Peter", "Erwan")

      // Let's count them
      def count[E]: Iteratee[E, Int] = Iteratee.fold(0)((c, _) => c + 1)
      println("#names = " + await(Iteratee.flatten(names |>> count).run))

      // Let's measure them
      def accumulateLength: Iteratee[String, Int] = Iteratee.fold(0)((l, e) => l + e.length)
      println("total length = " + await(Iteratee.flatten(names |>> accumulateLength).run))
      val toLength = Enumeratee.map[String] { s => s.length }
      val sum: Iteratee[Int, Int] = Iteratee.fold[Int, Int](0) { (s, e) => s + e }
      println("total length = " + await(Iteratee.flatten(names &> toLength |>> sum).run))

      val toBinary = Enumeratee.map[String](s => s.getBytes(UTF_8))
      def save[E](consumer: Iteratee[Array[Byte], E]): Future[E] = {
        val producer: Enumerator[Array[Byte]] = names &> toBinary
        Iteratee.flatten(producer(consumer)).run
      }
      def writeToStream(s: OutputStream) =
        Iteratee.
          foreach((e: Array[Byte]) => s.write(e)).
          mapDone(r => { s.close(); r })
      save(writeToStream(new FileOutputStream(File.createTempFile("test", null))))
    }
  }

  def await[T](f: Future[T]) = result(f, timeout)
}
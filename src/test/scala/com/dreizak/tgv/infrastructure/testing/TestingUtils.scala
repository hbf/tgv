package com.dreizak.tgv.infrastructure.testing

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object TestingUtils {
  def await[T](f: Future[T], timeout: Duration = Duration.Inf): T = Await.result(f, timeout)
}
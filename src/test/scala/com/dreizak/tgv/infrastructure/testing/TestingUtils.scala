package com.dreizak.tgv.infrastructure.testing

import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.Future
import com.dreizak.util.concurrent.CancellableFuture

object TestingUtils {
  def await[T](f: Future[T])(implicit timeout: Duration): T = Await.result(f, timeout)
}
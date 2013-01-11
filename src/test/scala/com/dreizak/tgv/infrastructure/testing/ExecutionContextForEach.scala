package com.dreizak.tgv.infrastructure.testing

import scala.concurrent.duration.DurationInt

import org.scalatest.{ BeforeAndAfterEach, Suite }

import com.dreizak.tgv.{ ExecutionContextService, SchedulingContext }

trait ExecutionContextForEach extends BeforeAndAfterEach {
  self: Suite =>

  val threadPoolSize = 10
  private var service: ExecutionContextService = _

  implicit var executionContext: SchedulingContext = _
  implicit val timeout = 30 seconds

  override def beforeEach = {
    super.beforeEach()
    service = new ExecutionContextService(threadPoolSize)

    service.start()
    executionContext = service.context
  }

  override def afterEach = {
    service.stop()
    super.afterEach()
  }
}
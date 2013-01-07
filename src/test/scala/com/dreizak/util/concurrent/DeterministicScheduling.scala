package com.dreizak.util.concurrent

import org.jmock.lib.concurrent.DeterministicScheduler
import com.dreizak.util.time.TimeProvider
import java.util.concurrent.TimeUnit
import org.scalatest.Suite
import org.scalatest.BeforeAndAfterEach
import scala.concurrent.ExecutionContext
import com.dreizak.tgv.infrastructure.testing.ExecutionContextForEach

trait DeterministicScheduling extends Suite with BeforeAndAfterEach {

  protected class TestingTimeProvider(s: DeterministicScheduler) extends TimeProvider {
    private var t = 0
    def now() = t
    def tick(): Unit = {
      s.tick(1, TimeUnit.MILLISECONDS)
      t = t + 1
    }
    def tick(nrOfTimesToTick: Int): Unit = (1 to nrOfTimesToTick).toList.foreach(_ => tick())
  }

  implicit var detScheduler: DeterministicScheduler = null
  var time: TestingTimeProvider = null
  implicit var deterministicContext: ExecutionContext = null

  override def beforeEach = {
    super.beforeEach()
    detScheduler = new DeterministicScheduler()
    time = new TestingTimeProvider(detScheduler)
    deterministicContext = ExecutionContext.fromExecutor(detScheduler, t => throw new IllegalStateException("Exception in deterministic scheduluer.", t))
  }
}
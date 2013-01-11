package com.dreizak.tgv

import com.dreizak.util.service.Service
import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.ExecutionContext._
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors.newScheduledThreadPool
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Callable
import java.util.Collection
import java.util.concurrent.ScheduledFuture

/**
 * Union interface since Java does not support union types
 *
 * This is currently needed because Scala 2.10 does not offer this type (it
 * only provides `ExecutionContextExecutorService`).
 */
trait SchedulingContext extends ExecutionContextExecutorService with ScheduledExecutorService

/**
 * A [[com.dreizak.util.service.Service]] that provides a Scala `ExecutionContextExecutorService`
 * in form of a fixed thread pool of a given size.
 *
 * The `stop` method shuts down the thread pool and awaits its termination; if the thread pool
 * does not terminate within duration `shutdownTimeout`, an `InterruptedException` is thrown.
 *
 * @param threadPoolSize size of the threadPool
 */
class ExecutionContextService(threadPoolSize: Int) extends Service {
  /**
   * The timeout for shutdown (see class documentation).
   */
  val shutdownTimeout = 10 minutes

  private var executionContext: SchedulingContext = null

  override def start() = {
    // Scala 2.10 does not offer a way to create a SchedulingContext
    val service = newScheduledThreadPool(threadPoolSize)
    val executor = fromExecutorService(service)
    executionContext = new SchedulingContext {
      override def execute(command: Runnable) = service.execute(command)
      override def shutdown() { service.shutdown() }
      override def shutdownNow() = service.shutdownNow()
      override def isShutdown = service.isShutdown
      override def isTerminated = service.isTerminated
      override def awaitTermination(l: Long, timeUnit: TimeUnit) = service.awaitTermination(l, timeUnit)
      override def submit[T](callable: Callable[T]) = service.submit(callable)
      override def submit[T](runnable: Runnable, t: T) = service.submit(runnable, t)
      override def submit(runnable: Runnable) = service.submit(runnable)
      override def invokeAll[T](callables: Collection[_ <: Callable[T]]) = service.invokeAll(callables)
      override def invokeAll[T](callables: Collection[_ <: Callable[T]], l: Long, timeUnit: TimeUnit) = service.invokeAll(callables, l, timeUnit)
      override def invokeAny[T](callables: Collection[_ <: Callable[T]]) = service.invokeAny(callables)
      override def invokeAny[T](callables: Collection[_ <: Callable[T]], l: Long, timeUnit: TimeUnit) = service.invokeAny(callables, l, timeUnit)

      override def reportFailure(t: Throwable): Unit = executor.reportFailure(t)
      override def schedule[V](c: Callable[V], l: Long, u: TimeUnit): ScheduledFuture[V] = service.schedule[V](c, l, u)
      override def schedule(r: Runnable, l: Long, u: TimeUnit): ScheduledFuture[_] = service.schedule(r, l, u)
      override def scheduleAtFixedRate(r: Runnable, l: Long, ll: Long, u: TimeUnit): ScheduledFuture[_] = service.scheduleAtFixedRate(r, l, ll, u)
      override def scheduleWithFixedDelay(r: Runnable, l: Long, ll: Long, u: TimeUnit): ScheduledFuture[_] = service.scheduleWithFixedDelay(r, l, ll, u)
    }
  }

  override def stop() = {
    executionContext.shutdown()
    executionContext.awaitTermination(shutdownTimeout.toSeconds, TimeUnit.SECONDS)
    executionContext = null
  }

  override def isRunning: Boolean = executionContext != null

  /**
   * The `ExecutionContextExecutorService` provided by this service.
   *
   * This is only defined if the service is started.
   */
  def context(): SchedulingContext = {
    require(executionContext != null, "You must call start() before using this service.")
    executionContext
  }
}
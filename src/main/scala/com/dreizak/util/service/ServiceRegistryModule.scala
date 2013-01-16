package com.dreizak.util.service

import com.google.inject.{AbstractModule, Binder, Injector, Key, Module, TypeLiteral}
import com.weiglewilczek.slf4s.Logging

import net.codingwell.scalaguice.ScalaModule

/**
 * A Guice `AbstractModule` that provides a `register` method to register `Services` that can
 * be started and stopped.
 *
 * You must call this class's `install` method and <em>not</em> use `binder.install`.
 *
 * FIXME: part of TGV?
 */
abstract class ServiceRegistryModule extends AbstractModule with ScalaModule with Logging {
  private var isRunning = false

  // The types of the registered services, or the service-registry modules installed using
  // `install` (in reverse order of installation)
  private var services = List[Either[TypeLiteral[Service], ServiceRegistryModule]]()

  def running(): Boolean = isRunning

  def start(injector: Injector): Unit = {
    if (!running()) {
      services.reverse.foreach {
        case Left(t) =>
          val service = injector.getInstance(Key.get(t))
          logger.info("Starting service " + service.getClass.getSimpleName + ".")
          service.start()
        case Right(module) =>
          logger.info("Starting services from module " + module.getClass.getSimpleName + "...")
          module.start(injector)
      }
      isRunning = true
    }
  }

  def stop(injector: Injector): Unit = {
    if (running()) {
      services.foreach {
        case Left(t) =>
          val service = injector.getInstance(Key.get(t))
          logger.info("Stopping service " + service.getClass.getSimpleName + ".")
          service.stop()
        case Right(module) =>
          logger.info("Stopping services from module " + module.getClass.getSimpleName + "...")
          module.stop(injector)
      }
      isRunning = false
    }
  }

  // TODO: can we improve this (ClassTag?) so we do not have to create type literals?
  def register[T <: Service](t: TypeLiteral[T]) = {
    val service = Left(t.asInstanceOf[TypeLiteral[Service]])
    if (!services.contains(service)) {
      logger.info("Registering service: " + t.getRawType.getSimpleName)
      services = service :: services
    }
  }

  override def install(module: Module): Unit = {
    module match {
      case m: ServiceRegistryModule =>
        // Only add the module if no other instance of it has been added already
        if (!services.exists({
          case Left(_) => false
          case Right(other) => other.getClass.getName == m.getClass.getName
        })) {
          services = Right(m) :: services
        }
      case _ =>
    }
    super.install(module)
  }

  override def binder(): Binder =
    throw new IllegalStateException("Instead of binder.XYZ(), call member XYZ() of ServiceRegistryModule directly.")
}
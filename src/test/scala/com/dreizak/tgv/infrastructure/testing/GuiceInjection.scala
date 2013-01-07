package com.dreizak.tgv.infrastructure.testing

import org.scalatest.{BeforeAndAfter, Suite}

import com.dreizak.util.service.ServiceRegistryModule
import com.google.inject.{Guice, Injector}

trait GuiceInjection extends ServiceRegistryModule with BeforeAndAfter {
  self: Suite =>

  private var injector: Injector = _

  before {
    injector = Guice.createInjector(this)
    injector.injectMembers(this)
    start(injector)
  }

  after {
    stop(injector)
  }
}
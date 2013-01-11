package com.dreizak.tgv.infrastructure.testing

import java.io.File

/**
 * A simple barrier for multi-JVM tests involving two tests.
 *
 * You should clean TODO and run tests sequentially TODO created in <tt>.</tt>
 */
trait MultiJvmTestBarrier {
  private val pollInterval = 50

  /**
   * Ensures that two JVMs that both call this method with the same argument both share a moment in time
   * within this method.
   */
  def barrier[C](c: C, name: String) = {
    if (create(name)) {
      // We created it (and before, it did not exist) so it's up for the other end to delete it
      println("Waiting at barrier '" + name + " (in " + c.getClass.getSimpleName + ")")
      waitUntil(!exists(name))
    } else {
      // Other end has created it already and is waiting for us to delete it
      delete(name)
    }
    Thread.sleep(10 * pollInterval) // Note: may fail due to scheduler!
  }

  def enterBarrier[C](c: C, name: String) = {
    println("Entering barrier '" + name + " (from " + c.getClass.getSimpleName + ")")
    barrier(c, name)
  }

  def exitBarrier[C](c: C, name: String) = {
    println("Leaving barrier '" + name + " (from " + c.getClass.getSimpleName + ")")
    barrier(c, name)
  }

  private def it(name: String) = new File("target" + File.separator + name)
  private def exists(name: String): Boolean = it(name).exists()
  private def create(name: String): Boolean = it(name).createNewFile()
  private def delete(name: String): Boolean = it(name).delete()

  private def waitUntil(p: => Boolean): Unit = while (!p) { Thread.sleep(pollInterval) }
}
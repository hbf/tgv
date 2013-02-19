package com.dreizak.util.service

/**
 * A service is a software component that can be started and stopped.
 *
 * FIXME: part of TGV?
 */
trait Service {
  /**
   * Starts the service.
   *
   * This method has no effect if `isRunning` is already `true`.
   */
  def start(): this.type

  /**
   * Stops the service.
   *
   * This method has no effect if `isRunning` is already `false`.
   */
  def stop(): this.type

  /**
   * True iff `start` has been called (and `stop` was not invoked in the meantime).
   */
  def isRunning(): Boolean
}
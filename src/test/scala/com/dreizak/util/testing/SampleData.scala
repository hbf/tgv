package com.dreizak.util.testing

import java.io.InputStream
import java.io.ByteArrayInputStream

/**
 * Data for tests.
 */
object SampleData {
  /**
   * Provides an input stream to a sample document `id`, which has length `length`.
   *
   * Note: the data for the stream may be kept in memory, so keep `length` reasonably small.
   */
  def streamForSample(id: Int, length: Int): ByteArrayInputStream = {
    val array = sampleArray(id, length)
    new ByteArrayInputStream(array)
  }

  def streamForSample(id: Int): InputStream = streamForSample(id, id * 10)

  def sampleArray(id: Int, length: Int): Array[Byte] = (1 to length).map(i => (i % (id + 1)).toByte).toArray
}
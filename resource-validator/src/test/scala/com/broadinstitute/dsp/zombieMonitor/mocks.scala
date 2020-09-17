package com.broadinstitute.dsp
package resourceValidator

import fs2.Stream
import cats.effect.IO

class FakeDbReader extends DbReader[IO] {
  override def getDeletedRuntimes: Stream[IO, Runtime] = Stream.empty

  override def getErroredRuntimes: Stream[IO, Runtime] = Stream.empty
}

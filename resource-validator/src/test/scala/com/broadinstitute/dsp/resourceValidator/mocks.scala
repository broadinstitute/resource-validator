package com.broadinstitute.dsp
package resourceValidator

import fs2.Stream
import cats.effect.IO
import org.broadinstitute.dsde.workbench.google2.GKEModels

class FakeDbReader extends DbReader[IO] {
  override def getDeletedRuntimes: Stream[IO, Runtime] = Stream.empty

  override def getErroredRuntimes: Stream[IO, Runtime] = Stream.empty

  override def getBucketsToDelete: Stream[IO, BucketToRemove] = Stream.empty

  override def getK8sClustersToDelete: Stream[IO, GKEModels.KubernetesClusterId] = Stream.empty

  override def getDeletedDisks: Stream[IO, Disk] = Stream.empty
}

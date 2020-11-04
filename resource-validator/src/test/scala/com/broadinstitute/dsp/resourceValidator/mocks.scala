package com.broadinstitute.dsp
package resourceValidator

import fs2.{Pipe, Stream}
import cats.effect.IO
import com.google.pubsub.v1.PubsubMessage
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.google2.GooglePublisher

class FakeDbReader extends DbReader[IO] {
  override def getDeletedRuntimes: Stream[IO, Runtime] = Stream.empty

  override def getErroredRuntimes: Stream[IO, Runtime] = Stream.empty

  override def getStagingBucketsToDelete: Stream[IO, BucketToRemove] = Stream.empty

  override def getKubernetesClustersToDelete: Stream[IO, KubernetesClusterToRemove] = Stream.empty

  override def getDeletedDisks: Stream[IO, Disk] = Stream.empty

  override def getInitBucketsToDelete: Stream[IO, InitBucketToRemove] = Stream.empty

  override def getDeletedAndErroredKubernetesClusters: Stream[IO, KubernetesCluster] = Stream.empty

  override def getDeletedAndErroredNodepools: Stream[IO, Nodepool] = Stream.empty
}

class FakeGooglePublisher extends GooglePublisher[IO] {
  override def publish[MessageType](implicit ev: Encoder[MessageType]): Pipe[IO, MessageType, Unit] =
    in => in.evalMap(_ => IO.unit)

  override def publishNative: Pipe[IO, PubsubMessage, Unit] = in => in.evalMap(_ => IO.unit)

  override def publishString: Pipe[IO, String, Unit] = in => in.evalMap(_ => IO.unit)
}

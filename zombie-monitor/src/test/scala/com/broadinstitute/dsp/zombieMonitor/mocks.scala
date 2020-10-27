package com.broadinstitute.dsp.zombieMonitor

import cats.effect.IO
import com.broadinstitute.dsp
import com.broadinstitute.dsp.{Disk, K8sClusterToScan, NodepoolToScan}
import fs2.Stream

class FakeDbReader extends DbReader[IO] {
  override def getDisksToDeleteCandidate: Stream[IO, Disk] = Stream.empty
  override def getk8sClustersToDeleteCandidate: Stream[IO, K8sClusterToScan] = Stream.empty
  override def getRuntimeCandidate: Stream[IO, dsp.Runtime] = Stream.empty
  override def getk8sNodepoolsToDeleteCandidate: Stream[IO, NodepoolToScan] = Stream.empty
  override def updateDiskStatus(id: Long): IO[Unit] = IO.unit
  override def updateK8sClusterStatus(id: Long): IO[Unit] = IO.unit
  override def updateNodepoolAndAppStatus(id: Long, status: String): IO[Unit] = IO.unit
  override def markRuntimeDeleted(id: Long): IO[Unit] = IO.unit
  override def updateRuntimeStatus(id: Long, status: String): IO[Unit] = IO.unit
  override def markNodepoolAndAppDeleted(nodepoolId: Long): IO[Unit] = IO.unit
  override def insertClusterError(clusterId: Long, errorCode: Option[Int], errorMessage: String): IO[Unit] = IO.unit
}

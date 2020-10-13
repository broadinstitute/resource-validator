package com.broadinstitute.dsp.zombieMonitor

import cats.effect.IO
import com.broadinstitute.dsp.{Disk, K8sClusterToScan, NodepoolToScan}
import fs2.Stream

class FakeDbReader extends DbReader[IO] {
  def getDisksToDeleteCandidate: Stream[IO, Disk] = Stream.empty
  def getk8sClustersToDeleteCandidate: Stream[IO, K8sClusterToScan] = Stream.empty
  def getk8sNodepoolsToDeleteCandidate: Stream[IO, NodepoolToScan] = Stream.empty
  def updateDiskStatus(id: Long): IO[Unit] = IO.unit
  def updateK8sClusterStatus(id: Long): IO[Unit] = IO.unit
  def markNodepoolAndAppStatusDeleted(id: Long): IO[Unit] = IO.unit
  def markNodepoolAndAppError(id: Long): IO[Unit] = IO.unit
}

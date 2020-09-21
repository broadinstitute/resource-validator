package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.{Async, _}
import fs2.Stream
import DbReaderImplicits._
import doobie._
import doobie.implicits._
import cats.implicits._

trait DbReader[F[_]] {
  def getDisksToDeleteCandidate: Stream[F, Disk]
  def getk8sClustersToDeleteCandidate: Stream[F, K8sClusterToScan]
  def updateDiskStatus(id: Long): F[Unit]
  def updateK8sClusterStatus(id: Long): F[Unit]
}

object DbReader {
  implicit def apply[F[_]](implicit ev: DbReader[F]): DbReader[F] = ev

  val activeDisksQuery =
    sql"""select id, googleProject, name from PERSISTENT_DISK where status != "Deleted";
        """.query[Disk]

  val activeK8sClustersQuery =
    sql"""select id, googleProject, location, clusterName from KUBERNETES_CLUSTER where status != "Deleted";
        """.query[K8sClusterToScan]

  def updateDiskStatusQuery(id: Int) =
    sql"""
           update PERSISTENT_DISK set status = "Deleted" where id = $id
           """.update

  def updateK8sClusterStatusQuery(id: Int) =
    sql"""
           update KUBERNETES_CLUSTER set status = "DELETED" where id = $id
           """.update

  def impl[F[_]: ContextShift](xa: Transactor[F])(implicit F: Async[F]): DbReader[F] = new DbReader[F] {

    override def getDisksToDeleteCandidate: Stream[F, Disk] =
      activeDisksQuery.stream.transact(xa)

    override def getk8sClustersToDeleteCandidate: Stream[F, K8sClusterToScan] =
      activeK8sClustersQuery.stream.transact(xa)

    override def updateDiskStatus(id: Long): F[Unit] =
      updateDiskStatusQuery(id.toInt).run.transact(xa).void

    override def updateK8sClusterStatus(id: Long): F[Unit] =
      updateK8sClusterStatusQuery(id.toInt).run.transact(xa).void
  }
}

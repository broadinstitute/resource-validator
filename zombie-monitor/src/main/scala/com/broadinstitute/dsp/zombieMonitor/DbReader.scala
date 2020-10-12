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
  def getk8sNodepoolsToDeleteCandidate: Stream[F, NodepoolToScan]
  def updateDiskStatus(id: Long): F[Unit]
  def updateK8sClusterStatus(id: Long): F[Unit]
  def markNodepoolAndAppStatusDeleted(id: Long): F[Unit]
  def markNodepoolError(id: Long): F[Unit]
}

object DbReader {
  implicit def apply[F[_]](implicit ev: DbReader[F]): DbReader[F] = ev

  val activeDisksQuery =
    sql"""select id, googleProject, name from PERSISTENT_DISK where status != "Deleted" and status != "Error";
        """.query[Disk]

  val activeK8sClustersQuery =
    sql"""select id, googleProject, location, clusterName from KUBERNETES_CLUSTER where status != "DELETED" and status != "ERROR";
        """.query[K8sClusterToScan]

  val activeNodepoolsQuery =
    sql"""select np.id, cluster.googleProject, cluster.location, cluster.clusterName, np.nodepoolName from 
         	NODEPOOL AS np INNER JOIN KUBERNETES_CLUSTER AS cluster 
         	on cluster.id = np.clusterId 
         	where np.status != "DELETED" and np.status != "ERROR"
         	""".query[NodepoolToScan]

  def updateDiskStatusQuery(id: Int) =
    sql"""
           update PERSISTENT_DISK set status = "Deleted", destroyedDate = now() where id = $id
           """.update

  def updateK8sClusterStatusQuery(id: Int) =
    sql"""
           update KUBERNETES_CLUSTER set status = "DELETED", destroyedDate = now() where id = $id
           """.update

  def updateNodepoolStatus(id: Long, status: String) =
    sql"""
           update NODEPOOL set status = $status, destroyedDate = now() where id = $id
           """.update

  def updateAppStatusForNodepoolId(nodepoolId: Long) =
    sql"""
           update APP set status = "DELETED", destroyedDate = now() where nodepoolId = $nodepoolId
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

    override def getk8sNodepoolsToDeleteCandidate: Stream[F, NodepoolToScan] =
      activeNodepoolsQuery.stream.transact(xa)

    override def markNodepoolAndAppStatusDeleted(id: Long): F[Unit] = {
      val res = for {
        _ <- updateNodepoolStatus(id, "DELETED").run
        _ <- updateAppStatusForNodepoolId(id).run
      } yield ()
      res.transact(xa)
    }

    def markNodepoolError(id: Long): F[Unit] = updateNodepoolStatus(id, "ERROR").run.transact(xa).void
  }
}

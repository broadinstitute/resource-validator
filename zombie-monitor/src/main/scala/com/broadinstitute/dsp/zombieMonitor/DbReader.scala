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
  def getRuntimeCandidate: Stream[F, Runtime]

  def updateDiskStatus(id: Long): F[Unit]
  def markRuntimeDeleted(id: Long): F[Unit]
  def updateRuntimeStatus(id: Long, status: String): F[Unit]
  def updateK8sClusterStatus(id: Long): F[Unit]
  def markNodepoolAndAppDeleted(nodepoolId: Long): F[Unit]
  def updateNodepoolAndAppStatus(nodepoolId: Long, status: String): F[Unit]
}

object DbReader {
  implicit def apply[F[_]](implicit ev: DbReader[F]): DbReader[F] = ev

  val activeDisksQuery =
    sql"""select id, googleProject, name from PERSISTENT_DISK where status != "Deleted" and status != "Error";
        """.query[Disk]

  // We only check runtimes that have been created for more than 1 hour because a newly "Creating" runtime may not exist in Google yet
  val activeRuntimeQuery =
    sql"""
         SELECT DISTINCT c1.id, googleProject, clusterName, rt.cloudService, c1.status FROM CLUSTER AS c1 
         INNER JOIN RUNTIME_CONFIG AS rt ON c1.`runtimeConfigId`=rt.id 
         WHERE c1.status!="Deleted" AND c1.status!="Error" AND createdDate < now() - INTERVAL 1 HOUR
        """.query[Runtime]

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

  def markNodepoolDeletedQuery(id: Long) =
    sql"""
           update NODEPOOL set status = "DELETED", destroyedDate = now() where id = $id
           """.update

  def markRuntimeDeletedQuery(id: Long) =
    sql"""
           update CLUSTER set status = "Deleted", destroyedDate = now() where id = $id
           """.update

  def updateRuntimeStatusQuery(id: Long, status: String) =
    sql"""
           update CLUSTER set status = $status where id = $id
           """.update

  def updateNodepoolStatus(id: Long, status: String) =
    sql"""
           update NODEPOOL set status = $status where id = $id
           """.update

  def markAppDeletedForNodepoolId(nodepoolId: Long) =
    sql"""
           update APP set status = "DELETED", destroyedDate = now() where nodepoolId = $nodepoolId
           """.update

  def updateAppStatusForNodepoolId(nodepoolId: Long, status: String) =
    sql"""
           update APP set status = $status where nodepoolId = $nodepoolId
           """.update

  def impl[F[_]: ContextShift](xa: Transactor[F])(implicit F: Async[F]): DbReader[F] = new DbReader[F] {
    override def getRuntimeCandidate: Stream[F, Runtime] = activeRuntimeQuery.stream.transact(xa)

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

    override def markNodepoolAndAppDeleted(nodepoolId: Long): F[Unit] = {
      val res = for {
        _ <- markNodepoolDeleted(nodepoolId).run
        _ <- markAppDeletedForNodepoolId(nodepoolId).run
      } yield ()
      res.transact(xa)
    }

    override def updateNodepoolAndAppStatus(nodepoolId: Long, status: String): F[Unit] = {
      val res = for {
        _ <- updateNodepoolStatus(nodepoolId, status).run
        _ <- updateAppStatusForNodepoolId(nodepoolId, status).run
      } yield ()
      res.transact(xa)
    }

    override def updateRuntimeStatus(id: Long, status: String): F[Unit] =
      updateRuntimeStatusQuery(id, status).run.transact(xa).void

    override def markRuntimeDeleted(id: Long): F[Unit] = markRuntimeDeletedQuery(id).run.transact(xa).void
  }
}

package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Async, _}
import doobie._
import doobie.implicits._
import fs2.Stream
import DbReaderImplicits._
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

trait DbReader[F[_]] {
  def getDeletedRuntimes: Stream[F, Runtime]
  def getDeletedDisks: Stream[F, Disk]
  def getErroredRuntimes: Stream[F, Runtime]
  def getBucketsToDelete: Stream[F, BucketToRemove]
  def getK8sClustersToDelete: Stream[F, KubernetesClusterId]
}

object DbReader {
  implicit def apply[F[_]](implicit ev: DbReader[F]): DbReader[F] = ev

  val deletedDisksQuery =
    sql"""
           select pd1.id, pd1.googleProject, pd1.name from PERSISTENT_DISK as pd1 where pd1.status="Deleted" and
           NOT EXISTS 
           (
             SELECT *
             FROM PERSISTENT_DISK pd2 
             WHERE pd1.googleProject = pd2.googleProject and pd1.name = pd2.name and pd2.status != "Deleted"
          )
        """.query[Disk]

  def impl[F[_]: ContextShift](xa: Transactor[F])(implicit F: Async[F]): DbReader[F] = new DbReader[F] {

    /**
     * AOU reuses runtime names, hence exclude any aou runtimes that have the same names that're still "alive"
     */
    override def getDeletedRuntimes: Stream[F, Runtime] =
      sql"""select distinct googleProject, clusterName, rt.cloudService from CLUSTER AS c1 
             INNER join RUNTIME_CONFIG AS rt ON c1.`runtimeConfigId`=rt.id WHERE c1.status="Deleted" 
             and NOT EXISTS (SELECT * from CLUSTER as c2 where c2.googleProject = c1.googleProject 
             and c2.clusterName=c1.clusterName and (c2.status="Stopped" or c2.status="Running"));"""
        .query[Runtime]
        .stream
        .transact(xa)

    override def getErroredRuntimes: Stream[F, Runtime] =
      sql"""select distinct googleProject, clusterName, rt.cloudService from CLUSTER AS c1 
             INNER join RUNTIME_CONFIG AS rt ON c1.`runtimeConfigId`=rt.id WHERE c1.status="Error" 
             and NOT EXISTS (SELECT * from CLUSTER as c2 where c2.googleProject = c1.googleProject 
             and c2.clusterName=c1.clusterName and (c2.status="Stopped" or c2.status="Running"));"""
        .query[Runtime]
        .stream
        .transact(xa)

    // When we delete runtimes, we keep their staging buckets for 10 days. Hence we're only deleting staging buckets whose
    // runtimes have been deleted more than 15 days ago.
    // Checker will blindly delete all buckets returned by this function. Since we've started running the cron job daily,
    // We really only need to delete any new buckets; hence we're skipping buckets whose runtimes were deleted more than 20 days ago
    override def getBucketsToDelete: Stream[F, BucketToRemove] =
      sql"""select googleProject, stagingBucket from CLUSTER WHERE status="Deleted" and destroyedDate < now() - interval 15 DAY and destroyedDate > now() - interval 20 DAY;"""
        .query[BucketToRemove]
        .stream
        .transact(xa)

    // Return all non-deleted clusters with non-default nodepools that are non-deleted and non-errored
    // We are excluding clusters with only default nodepools running on them so we do not remove batch-pre-created clusters.
    // We are calculating the grace period for cluster deletion assuming that the following are valid indicators for an app's last activity
    //  1. destroyedDate for deleted apps
    //  2. dateAccessed for error'ed apps
    override def getK8sClustersToDelete: Stream[F, KubernetesClusterId] =
      // TODO Check destroyedDate for DELETED apps and dateAccessed for ERROR'ed apps
      sql"""SELECT DISTINCT kc.id, kc.location, kc.clusterName
            FROM KUBERNETES_CLUSTER AS kc
            LEFT JOIN NODEPOOL AS np ON kc.id = np.clusterId
            LEFT JOIN APP AS app ON np.id = app.nodepoolId
            WHERE
              kc.status != "DELETED" AND
              NOT EXISTS (
                SELECT *
                FROM NODEPOOL AS n
                LEFT JOIN APP AS a ON n.id = a.nodepoolId
                WHERE kc.id = n.clusterId AND n.isDefault = 0 AND a.status != "DELETED" AND a.status !="ERROR"
              );
           """
        .query[KubernetesClusterId]
        .stream
        .transact(xa)

    // Same disk names might be re-used
    override def getDeletedDisks: Stream[F, Disk] =
      deletedDisksQuery.stream.transact(xa)
  }
}

final case class BucketToRemove(googleProject: GoogleProject, bucket: Option[GcsBucketName]) {
  override def toString: String = s"${googleProject.value},${bucket.getOrElse("null")}"
}

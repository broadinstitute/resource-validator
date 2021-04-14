package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Async, _}
import doobie._
import doobie.implicits._
import fs2.Stream
import DbReaderImplicits._
//import com.broadinstitute.dsp.RemovableNodepoolStatus
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

trait DbReader[F[_]] {
  def getDeletedDisks: Stream[F, Disk]
  def getDeletedRuntimes: Stream[F, Runtime]
  def getErroredRuntimes: Stream[F, Runtime]
  def getStoppedRuntimes: Stream[F, Runtime]
  def getStagingBucketsToDelete: Stream[F, BucketToRemove]
  def getKubernetesClustersToDelete: Stream[F, KubernetesClusterToRemove]
  def getInitBucketsToDelete: Stream[F, InitBucketToRemove]
  def getDeletedAndErroredKubernetesClusters: Stream[F, KubernetesCluster]
  def getDeletedAndErroredNodepools: Stream[F, Nodepool]
  def getRuntimesWithWorkers: Stream[F, RuntimeWithWorkers]
  def getNodepoolsToDelete: Stream[F, Nodepool]

}

object DbReader {
  implicit def apply[F[_]](implicit ev: DbReader[F]): DbReader[F] = ev

  val deletedDisksQuery =
    sql"""
           select pd1.id, pd1.googleProject, pd1.name, pd1.zone, pd1.formattedBy, pd1.release
           FROM PERSISTENT_DISK AS pd1
           WHERE pd1.status="Deleted" AND
             NOT EXISTS
             (
               SELECT *
               FROM PERSISTENT_DISK pd2
               WHERE pd1.googleProject = pd2.googleProject and pd1.name = pd2.name and pd2.status != "Deleted"
              )
        """.query[Disk]

  val initBucketsToDeleteQuery =
    sql"""select googleProject, initBucket from CLUSTER WHERE status="Deleted";"""
      .query[InitBucketToRemove]

  val deletedRuntimeQuery =
    sql"""SELECT DISTINCT c1.id, googleProject, clusterName, rt.cloudService, c1.status, rt.zone, rt.region
          FROM CLUSTER AS c1
          INNER JOIN RUNTIME_CONFIG AS rt ON c1.runtimeConfigId = rt.id
          WHERE
            c1.status = "Deleted" AND
            c1.destroyedDate > now() - INTERVAL 30 DAY AND
            NOT EXISTS (
              SELECT *
              FROM CLUSTER AS c2
              WHERE
                c2.googleProject = c1.googleProject AND
                c2.clusterName = c1.clusterName AND
                (c2.status = "Stopped" OR c2.status = "Running")
          )"""
      .query[Runtime]

  val erroredRuntimeQuery =
    sql"""SELECT DISTINCT c1.id, googleProject, clusterName, rt.cloudService, c1.status, rt.zone, rt.region
          FROM CLUSTER AS c1
          INNER JOIN RUNTIME_CONFIG AS rt ON c1.runtimeConfigId = rt.id
          WHERE
           c1.status = "Error" AND
           NOT EXISTS (
              SELECT *
              FROM CLUSTER AS c2
              WHERE
                c2.googleProject = c1.googleProject AND
                c2.clusterName=c1.clusterName AND
                (c2.status = "Stopped" OR c2.status = "Running")
             )"""
      .query[Runtime]

  val stoppedRuntimeQuery =
    sql"""SELECT DISTINCT c1.id, c1.googleProject, c1.clusterName, rt.cloudService, c1.status, rt.zone, rt.region
          FROM CLUSTER AS c1
          INNER JOIN RUNTIME_CONFIG AS rt ON c1.runtimeConfigId = rt.id
          WHERE
            c1.status = "Stopped" AND
            NOT EXISTS (
              SELECT *
              FROM CLUSTER AS c2
              WHERE
                c2.googleProject = c1.googleProject AND
                c2.clusterName = c1.clusterName AND
                c2.status = "Running"
             )"""
      .query[Runtime]

  val deletedAndErroredKubernetesClusterQuery =
    sql"""SELECT kc1.clusterName, googleProject, location
          FROM KUBERNETES_CLUSTER as kc1
          WHERE kc1.status="DELETED" OR kc1.status="ERROR"
          """
      .query[KubernetesCluster]

  // We are calculating the grace period for nodepool deletion assuming that the following are valid proxies for an app's last activity:
  //    1. destroyedDate for deleted apps
  //    2. createdDate for error'ed apps
  // Note that we explicitly check nodepools with 5/11 of statuses that exist.
  // The statuses we exclude are PROVISIONING, STOPPING, DELETED, PRECREATING, PREDELETING, and PREDELETING
  //    - We exclude all -ING statuses because they are transitionary, and the purpose of this is not to handle timeouts
  //    - Unclaimed we exclude because we never want to clean up batch created nodepools
  // We are excluding default nodepools, as these should remain for the lifetime of the cluster
  // TODO: Read the grace period (hardcoded to '1 HOUR' below) from config
  val applessNodepoolQuery =
    sql"""SELECT np.id, np.nodepoolName, kc.clusterName, kc.googleProject, kc.location
         FROM NODEPOOL AS np
         INNER JOIN KUBERNETES_CLUSTER AS kc
         ON np.clusterId = kc.id
         WHERE
            (
                np.status IN ("STATUS_UNSPECIFIED", "RUNNING", "RECONCILING", "ERROR", "RUNNING_WITH_ERROR")
                AND np.isDefault = 0
                AND NOT EXISTS
                (
                    SELECT * FROM APP AS a
                    WHERE np.id = a.nodepoolId
                    AND
                    (
                        (a.status != "DELETED" AND a.status != "ERROR")
                        OR (a.status = "DELETED" AND a.destroyedDate > now() - INTERVAL 1 HOUR)
                        OR (a.status = "ERROR" AND a.createdDate > now() - INTERVAL 1 HOUR)
                        OR (a.id IS NULL)
                    )
                )
             )
         """
      .query[Nodepool]

  val deletedAndErroredNodepoolQuery =
    sql"""SELECT np. id, np.nodepoolName, kc.clusterName, kc.googleProject, kc.location
         FROM NODEPOOL AS np
         INNER JOIN KUBERNETES_CLUSTER AS kc ON np.clusterId = kc.id
         WHERE np.status="DELETED" OR np.status="ERROR"
         """
      .query[Nodepool]

  // Return all non-deleted clusters with non-default nodepools that have apps that were all deleted
  // or errored outside the grace period (1 hour)
  //
  // We are excluding clusters with only default nodepools running on them so we do not remove batch-pre-created clusters.
  // We are calculating the grace period for cluster deletion assuming that the following are valid proxies for an app's last activity:
  //    1. destroyedDate for deleted apps
  //    2. createdDate for error'ed apps
  // TODO: Read the grace period (hardcoded to '1 HOUR' below) from config
  val kubernetesClustersToDeleteQuery =
    sql"""
            SELECT kc.id, kc.googleProject
            FROM KUBERNETES_CLUSTER kc
            WHERE
              kc.status != "DELETED" AND
              NOT EXISTS (
                SELECT *
                FROM NODEPOOL np
                LEFT JOIN APP a ON np.id = a.nodepoolId
                WHERE
                  kc.id = np.clusterId AND np.isDefault = 0 AND
                  (
                    (a.status != "DELETED" AND a.status != "ERROR") OR
                    (a.status = "DELETED" AND a.destroyedDate > now() - INTERVAL 1 HOUR) OR
                    (a.status = "ERROR" AND a.createdDate > now() - INTERVAL 1 HOUR) OR
                    (a.id IS NULL)
                  )
              );
         """
      .query[KubernetesClusterToRemove]

  // We're excluding cluster id 6220 because it's a known anomaly and user ed team has reached out to hufengzhou@g.harvard.edu
  val dataprocClusterWithWorkersQuery =
    sql"""SELECT DISTINCT c1.id, googleProject, clusterName, rt.cloudService, c1.status, rt.region, rt.numberOfWorkers, rt.numberOfPreemptibleWorkers
          FROM CLUSTER AS c1
          INNER JOIN RUNTIME_CONFIG AS rt ON c1.`runtimeConfigId`=rt.id
          WHERE 
            rt.cloudService="DATAPROC" AND 
            NOT c1.status="DELETED" AND
            c1.id != 6220
         """
      .query[RuntimeWithWorkers]

  def impl[F[_]: ContextShift](xa: Transactor[F])(implicit F: Async[F]): DbReader[F] = new DbReader[F] {

    /**
     * AOU reuses runtime names, hence exclude any aou runtimes that have the same names that're still "alive"
     */
    override def getDeletedRuntimes: Stream[F, Runtime] =
      deletedRuntimeQuery.stream.transact(xa)

    override def getErroredRuntimes: Stream[F, Runtime] =
      erroredRuntimeQuery.stream.transact(xa)

    override def getStoppedRuntimes: Stream[F, Runtime] =
      stoppedRuntimeQuery.stream.transact(xa)

    // When we delete runtimes, we keep their staging buckets for 10 days. Hence we're only deleting staging buckets whose
    // runtimes have been deleted more than 15 days ago.
    // Checker will blindly delete all buckets returned by this function. Since we've started running the cron job daily,
    // We really only need to delete any new buckets; hence we're skipping buckets whose runtimes were deleted more than 20 days ago
    override def getStagingBucketsToDelete: Stream[F, BucketToRemove] =
      sql"""select googleProject, stagingBucket from CLUSTER WHERE status="Deleted" and destroyedDate < now() - interval 15 DAY and destroyedDate > now() - interval 20 DAY;"""
        .query[BucketToRemove]
        .stream
        .transact(xa)

    override def getInitBucketsToDelete: Stream[F, InitBucketToRemove] =
      initBucketsToDeleteQuery.stream
        .transact(xa)

    override def getKubernetesClustersToDelete: Stream[F, KubernetesClusterToRemove] =
      kubernetesClustersToDeleteQuery.stream.transact(xa)

    // Same disk names might be re-used
    override def getDeletedDisks: Stream[F, Disk] =
      deletedDisksQuery.stream.transact(xa)

    override def getDeletedAndErroredKubernetesClusters: Stream[F, KubernetesCluster] =
      deletedAndErroredKubernetesClusterQuery.stream.transact(xa)

    override def getDeletedAndErroredNodepools: Stream[F, Nodepool] =
      deletedAndErroredNodepoolQuery.stream.transact(xa)

    override def getRuntimesWithWorkers: Stream[F, RuntimeWithWorkers] =
      dataprocClusterWithWorkersQuery.stream.transact(xa)

    override def getNodepoolsToDelete: Stream[F, Nodepool] =
      applessNodepoolQuery.stream.transact(xa)

  }
}

final case class BucketToRemove(googleProject: GoogleProject, bucket: Option[GcsBucketName]) {
  override def toString: String = s"${googleProject.value},${bucket.getOrElse("null")}"
}

package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Async, _}
import doobie._
import doobie.implicits._
import fs2.Stream
import DbReaderImplicits._
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

trait DbReader[F[_]] {
  def getDeletedRuntimes: Stream[F, Runtime]
  def getErroredRuntimes: Stream[F, Runtime]
  def getBucketsToDelete: Stream[F, BucketToRemove]
}

object DbReader {
  implicit def apply[F[_]](implicit ev: DbReader[F]): DbReader[F] = ev

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

    override def getBucketsToDelete: Stream[F, BucketToRemove] =
      sql"""select googleProject, stagingBucket from CLUSTER WHERE status="Deleted" and destroyedDate < now() - interval 15 DAY;"""
        .query[BucketToRemove]
        .stream
        .transact(xa)
  }
}

final case class BucketToRemove(googleProject: GoogleProject, bucket: Option[GcsBucketName])

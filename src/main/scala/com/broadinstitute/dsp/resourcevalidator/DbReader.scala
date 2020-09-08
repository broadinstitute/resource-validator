package com.broadinstitute.dsp.resourcevalidator

import cats.effect.{Async, _}
import cats.implicits._
import doobie._
import doobie.implicits._
import fs2.Stream

trait DbReader[F[_]] {
  def getDeletedRuntimes: Stream[F, Runtime]
  def getErroredRuntimes: Stream[F, Runtime]
}

object DbReader {
  implicit def apply[F[_]](implicit ev: DbReader[F]): DbReader[F] = ev

  implicit val cloudServiceGet: Get[CloudService] = Get[String].temap(s =>
    s match {
      case "DATAPROC" => CloudService.Dataproc.asRight[String]
      case "GCE"      => CloudService.Gce.asRight[String]
      case x          => s"invalid cloudService value ${x}".asLeft[CloudService]
    }
  )

  def iml[F[_]: ContextShift](xa: Transactor[F])(implicit F: Async[F]): DbReader[F] = new DbReader[F] {
    override def getDeletedRuntimes: Stream[F, Runtime] =
      sql"""select distinct googleProject, clusterName, rt.cloudService from CLUSTER AS C 
             INNER join RUNTIME_CONFIG AS rt ON C.`runtimeConfigId`=rt.id WHERE C.status="Deleted" 
             and NOT EXISTS (SELECT * from CLUSTER as c2 where c2.googleProject = C.googleProject 
             and c2.clusterName=C.clusterName and (c2.status="Stopped" or c2.status="Running"));"""
        .query[Runtime]
        .stream
        .transact(xa)

    override def getErroredRuntimes: Stream[F, Runtime] =
      sql"""select distinct googleProject, clusterName, rt.cloudService from CLUSTER AS C 
             INNER join RUNTIME_CONFIG AS rt ON C.`runtimeConfigId`=rt.id WHERE C.status="Error" 
             and NOT EXISTS (SELECT * from CLUSTER as c2 where c2.googleProject = C.googleProject 
             and c2.clusterName=C.clusterName and (c2.status="Stopped" or c2.status="Running"));"""
        .query[Runtime]
        .stream
        .transact(xa)
  }
}

package com.broadinstitute.dsp

import cats.syntax.all._
import doobie.{Get, Meta, Read}
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterName
import org.broadinstitute.dsde.workbench.google2.{DiskName, Location, RegionName, ZoneName}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}
import doobie.implicits.javasql.TimestampMeta

import java.sql.Timestamp
import java.time.Instant

object DbReaderImplicits {
  implicit val cloudServiceGet: Get[CloudService] = Get[String].temap {
    case "DATAPROC" => CloudService.Dataproc.asRight[String]
    case "GCE"      => CloudService.Gce.asRight[String]
    case x          => s"invalid cloudService value $x".asLeft[CloudService]
  }

  implicit val instantMeta: Meta[Instant] = Meta[Timestamp].imap(_.toInstant)(Timestamp.from)
  implicit val gcsBucketNameGet: Get[GcsBucketName] = Get[String].map(GcsBucketName)
  implicit val initBucketNameGet: Get[InitBucketName] = Get[String].temap(s => InitBucketName.withValidation(s))
  implicit val locationGet: Get[Location] = Get[String].map(Location)
  implicit val kubernetesClusterNameGet: Get[KubernetesClusterName] = Get[String].map(KubernetesClusterName)
  implicit val diskNameMeta: Meta[DiskName] = Meta[String].imap(DiskName)(_.value)
  implicit val zoneNameMeta: Meta[ZoneName] = Meta[String].imap(ZoneName(_))(_.value)
  implicit val googleProjectMeta: Meta[GoogleProject] = Meta[String].imap(GoogleProject)(_.value)
  implicit val k8sClusterNameMeta: Meta[KubernetesClusterName] = Meta[String].imap(KubernetesClusterName)(_.value)
  implicit val locationMeta: Meta[Location] = Meta[String].imap(Location)(_.value)
  implicit val regionNameMeta: Meta[RegionName] = Meta[String].imap(RegionName(_))(_.value)
  implicit val runtimeRead: Read[Runtime] =
    Read[(Long, GoogleProject, String, CloudService, String, Option[ZoneName], Option[RegionName])].map {
      case (id, project, runtimeName, cloudService, status, zone, region) =>
        (zone, region) match {
          case (Some(_), Some(_)) =>
            throw new RuntimeException(
              s"${cloudService} Runtime ${id} has both zone and region defined. This is impossible. Fix this in DB"
            )
          case (Some(z), None) =>
            if (cloudService == CloudService.Gce)
              Runtime.Gce(id, project, runtimeName, cloudService, status, z)
            else
              throw new RuntimeException(
                s"Dataproc runtime ${id} has no region defined. This is impossible. Fix this in DB"
              )
          case (None, Some(r)) =>
            if (cloudService == CloudService.Dataproc)
              Runtime.Dataproc(id, project, runtimeName, cloudService, status, r)
            else
              throw new RuntimeException(
                s"Gce runtime ${id} has no zone defined. This is impossible. Fix this in DB"
              )
          case (None, None) =>
            throw new RuntimeException(
              s"${cloudService} Runtime ${id} has no zone and no region defined. This is impossible. Fix this in DB"
            )
        }
    }
}

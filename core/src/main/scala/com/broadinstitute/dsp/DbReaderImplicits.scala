package com.broadinstitute.dsp

import java.sql.Timestamp
import java.time.Instant

import cats.implicits._
import doobie.{Get, Meta}
import doobie.implicits.javasql._
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterName
import org.broadinstitute.dsde.workbench.google2.{DiskName, Location, RegionName, ZoneName}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

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
  implicit val zoneNameMeta: Meta[ZoneName] = Meta[String].imap(ZoneName)(_.value)
  implicit val googleProjectMeta: Meta[GoogleProject] = Meta[String].imap(GoogleProject)(_.value)
  implicit val k8sClusterNameMeta: Meta[KubernetesClusterName] = Meta[String].imap(KubernetesClusterName)(_.value)
  implicit val locationMeta: Meta[Location] = Meta[String].imap(Location)(_.value)
  implicit val regionNameMeta: Meta[RegionName] = Meta[String].imap(RegionName)(_.value)
}

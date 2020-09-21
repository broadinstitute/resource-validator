package com.broadinstitute.dsp

import cats.implicits._
import doobie.{Get, Meta}
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterName
import org.broadinstitute.dsde.workbench.google2.{DiskName, Location, ZoneName}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

object DbReaderImplicits {
  implicit val cloudServiceGet: Get[CloudService] = Get[String].temap(s =>
    s match {
      case "DATAPROC" => CloudService.Dataproc.asRight[String]
      case "GCE"      => CloudService.Gce.asRight[String]
      case x          => s"invalid cloudService value ${x}".asLeft[CloudService]
    }
  )

  implicit val gcsBucketNameGet: Get[GcsBucketName] = Get[String].map(GcsBucketName)
  implicit val locationGet: Get[Location] = Get[String].map(Location)
  implicit val kubernetesClusterNameGet: Get[KubernetesClusterName] = Get[String].map(KubernetesClusterName)
  implicit val diskNameMeta: Meta[DiskName] = Meta[String].imap(DiskName)(_.value)
  implicit val zoneNameMeta: Meta[ZoneName] = Meta[String].imap(ZoneName)(_.value)
  implicit val googleProjectMeta: Meta[GoogleProject] = Meta[String].imap(GoogleProject)(_.value)
}

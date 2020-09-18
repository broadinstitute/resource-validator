package com.broadinstitute.dsp

import cats.implicits._
import doobie.Get
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterName
import org.broadinstitute.dsde.workbench.google2.{DiskName, Location}
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName

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
  implicit val diskNameGet: Get[DiskName] = Get[String].map(DiskName)
}

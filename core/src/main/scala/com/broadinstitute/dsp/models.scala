package com.broadinstitute.dsp

import org.broadinstitute.dsde.workbench.google2.DiskName
import org.broadinstitute.dsde.workbench.google2.GKEModels.{KubernetesClusterId, NodepoolId}
import org.broadinstitute.dsde.workbench.model.ValueObject
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

sealed abstract class CloudService extends Product with Serializable
object CloudService {
  final case object Gce extends CloudService
  final case object Dataproc extends CloudService
}
final case class Disk(id: Long, googleProject: GoogleProject, diskName: DiskName) {
  override def toString: String = s"${id}/${googleProject.value},${diskName.value}"
}

//init buckets are different than staging buckets because we store them with gs://[GcsBucketName]/
case class InitBucketName(value: String) extends ValueObject with Product with Serializable {
  require(value.startsWith("gs://"))
  require(value.endsWith("/"))
  val asGcsBucketName: GcsBucketName = GcsBucketName(value.trim.subSequence(5, value.trim.length - 1).toString)
}

final case class InitBucketToRemove(googleProject: GoogleProject, bucket: Option[InitBucketName]) {
  override def toString: String = s"${googleProject.value},${bucket.getOrElse("null")}"
}

final case class K8sClusterToScan(id: Long, kubernetesClusterId: KubernetesClusterId)
final case class NodepoolToScan(id: Long, nodepoolId: NodepoolId)

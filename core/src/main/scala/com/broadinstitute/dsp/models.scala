package com.broadinstitute.dsp

import org.broadinstitute.dsde.workbench.google2.DiskName
import org.broadinstitute.dsde.workbench.google2.GKEModels.{KubernetesClusterId, NodepoolId}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

sealed abstract class CloudService extends Product with Serializable
object CloudService {
  final case object Gce extends CloudService
  final case object Dataproc extends CloudService
}
final case class Disk(id: Long, googleProject: GoogleProject, diskName: DiskName) {
  override def toString: String = s"${id}/${googleProject.value},${diskName.value}"
}
final case class K8sClusterToScan(id: Long, kubernetesClusterId: KubernetesClusterId)
final case class NodepoolToScan(id: Long, nodepoolId: NodepoolId)

final case class KubernetesClusterToRemove(id: Long, googleProject: GoogleProject)
// TODO: 'project' below is unnecessary but removing it requires an accompanying change in back Leo so leaving for now
final case class DeleteKubernetesClusterMessage(clusterId: Long, project: GoogleProject, traceId: Option[TraceId]) {
  val messageType: String = "deleteKubernetesCluster"
}

package com.broadinstitute.dsp

import enumeratum.EnumEntry
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.google2.DiskName
import org.broadinstitute.dsde.workbench.google2.JsonCodec.traceIdEncoder
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
final case class DeleteKubernetesClusterMessage(clusterId: Long,
                                                project: GoogleProject,
                                                traceId: Option[TraceId]) {
  val messageType: String = "deleteKubernetesCluster"
}

object JsonCodec {
  implicit val googleProjectEncoder: Encoder[GoogleProject] = Encoder.encodeString.contramap(_.value)
}

object LeoPubsubCodec {
  import JsonCodec._

  sealed trait LeoPubsubMessageType extends EnumEntry with Serializable with Product {
    def asString: String
    override def toString = asString
  }

  implicit val leoPubsubMessageTypeEncoder: Encoder[LeoPubsubMessageType] = Encoder.encodeString.contramap(_.asString)

  implicit val deleteKubernetesClusterMessageEncoder: Encoder[DeleteKubernetesClusterMessage] =
    Encoder.forProduct4("messageType", "clusterId", "project", "traceId")(x =>
      (x.messageType, x.clusterId, x.project, x.traceId) )
}

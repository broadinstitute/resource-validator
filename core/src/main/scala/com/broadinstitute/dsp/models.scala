package com.broadinstitute.dsp

import org.broadinstitute.dsde.workbench.google2.DiskName
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

sealed abstract class CloudService extends Product with Serializable
object CloudService {
  final case object Gce extends CloudService
  final case object Dataproc extends CloudService
}
final case class Disk(googleProject: GoogleProject, diskName: DiskName) {
  override def toString: String = s"${googleProject.value},${diskName.value}"
}

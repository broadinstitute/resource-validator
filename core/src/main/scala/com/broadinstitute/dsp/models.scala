package com.broadinstitute.dsp

sealed abstract class CloudService extends Product with Serializable
object CloudService {
  final case object Gce extends CloudService
  final case object Dataproc extends CloudService
}

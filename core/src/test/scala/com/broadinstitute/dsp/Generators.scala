package com.broadinstitute.dsp

import org.broadinstitute.dsde.workbench.google2.GKEModels.{KubernetesClusterId, KubernetesClusterName}
import org.scalacheck.{Arbitrary, Gen}
import org.broadinstitute.dsde.workbench.google2.Generators._
import org.broadinstitute.dsde.workbench.google2.Location

object Generators {
  val genCloudService: Gen[CloudService] = Gen.oneOf(CloudService.Gce, CloudService.Dataproc)
  val genRuntime: Gen[Runtime] = for {
    cloudService <- genCloudService
    project <- genGoogleProject
    runtimeName <- Gen.uuid.map(_.toString)
  } yield Runtime(project, runtimeName, cloudService)
  val genDisk: Gen[Disk] = for {
    id <- Gen.chooseNum(0, 100)
    project <- genGoogleProject
    diskName <- genDiskName
  } yield Disk(id, project, diskName)
  val genLocation = for {
    zoneLetter <- Gen.oneOf('a', 'b', 'c')
  } yield Location(s"us-central1-${zoneLetter}")
  //TODO: move to google2
  val genKubernetesClusterId = for {
    project <- genGoogleProject
    location <- genLocation
    clusterName <- Gen.uuid.map(x => KubernetesClusterName(x.toString))
  } yield KubernetesClusterId(project, location, clusterName)

  val genK8sClusterToScan: Gen[K8sClusterToScan] = for {
    id <- Gen.chooseNum(0, 100)
    clusterId <- genKubernetesClusterId
  } yield K8sClusterToScan(id, clusterId)

  implicit val arbRuntime: Arbitrary[Runtime] = Arbitrary(genRuntime)
  implicit val arbDisk: Arbitrary[Disk] = Arbitrary(genDisk)
  implicit val arbKubernetesClusterId: Arbitrary[KubernetesClusterId] = Arbitrary(genKubernetesClusterId)
  implicit val arbK8sClusterToScan: Arbitrary[K8sClusterToScan] = Arbitrary(genK8sClusterToScan)
}

package com.broadinstitute.dsp

import org.broadinstitute.dsde.workbench.google2.GKEModels.{
  KubernetesClusterId,
  KubernetesClusterName,
  NodepoolId,
  NodepoolName
}
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
  val genNodepoolName = Gen.uuid.map(x => NodepoolName(x.toString))
  val genNodepoolId = for {
    clusterId <- genKubernetesClusterId
    nodepoolName <- genNodepoolName
  } yield NodepoolId(clusterId, nodepoolName)

  val genInitBucket: Gen[InitBucketToRemove] = for {
    project <- genGoogleProject
    bucketName <- genGcsBucketName
  } yield InitBucketToRemove(project, Some(InitBucketName(bucketName.value)))

  val genK8sClusterToScan: Gen[K8sClusterToScan] = for {
    id <- Gen.chooseNum(0, 100)
    clusterId <- genKubernetesClusterId
  } yield K8sClusterToScan(id, clusterId)

  val genNodepoolToScan: Gen[NodepoolToScan] = for {
    id <- Gen.chooseNum(0, 100)
    nodepoolId <- genNodepoolId
  } yield NodepoolToScan(id, nodepoolId)

  implicit val arbRuntime: Arbitrary[Runtime] = Arbitrary(genRuntime)
  implicit val arbDisk: Arbitrary[Disk] = Arbitrary(genDisk)
  implicit val arbInitBucket: Arbitrary[InitBucketToRemove] = Arbitrary(genInitBucket)
  implicit val arbKubernetesClusterId: Arbitrary[KubernetesClusterId] = Arbitrary(genKubernetesClusterId)
  implicit val arbK8sClusterToScan: Arbitrary[K8sClusterToScan] = Arbitrary(genK8sClusterToScan)
  implicit val arbNodepoolToScan: Arbitrary[NodepoolToScan] = Arbitrary(genNodepoolToScan)
}

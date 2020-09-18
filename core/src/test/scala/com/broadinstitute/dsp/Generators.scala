package com.broadinstitute.dsp

import org.scalacheck.{Arbitrary, Gen}
import org.broadinstitute.dsde.workbench.google2.Generators._

object Generators {
  val genCloudService: Gen[CloudService] = Gen.oneOf(CloudService.Gce, CloudService.Dataproc)
  val genRuntime: Gen[Runtime] = for {
    cloudService <- genCloudService
    project <- genGoogleProject
    runtimeName <- Gen.uuid.map(_.toString)
  } yield Runtime(project, runtimeName, cloudService)
  val genDisk: Gen[Disk] = for {
    project <- genGoogleProject
    diskName <- genDiskName
  } yield Disk(project, diskName)

  implicit val arbRuntime: Arbitrary[Runtime] = Arbitrary(genRuntime)
  implicit val arbDisk: Arbitrary[Disk] = Arbitrary(genDisk)
}

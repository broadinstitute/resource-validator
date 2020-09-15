package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.IO
import cats.mtl.ApplicativeAsk
import org.broadinstitute.dsde.workbench.google2.{
  DataprocClusterName,
  GoogleComputeService,
  GoogleDataprocService,
  GoogleStorageService,
  InstanceName,
  RegionName,
  ZoneName
}
import org.broadinstitute.dsde.workbench.google2.mock.{
  BaseFakeGoogleDataprocService,
  FakeGoogleComputeService,
  FakeGoogleDataprocService,
  FakeGoogleStorageInterpreter
}
import org.scalatest.flatspec.AnyFlatSpec
import fs2.Stream
import com.broadinstitute.dsp.Generators._
import com.google.cloud.compute.v1.{Instance, Operation}
import com.google.cloud.dataproc.v1.{Cluster, ClusterOperationMetadata}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

class DeletedRuntimeCheckerSpec extends AnyFlatSpec with CronJobsTestSuite {
  val config = Config.appConfig.toOption.get

  it should "return None if runtime no longer exists in Google" in {
    val computeService = new FakeGoogleComputeService {
      override def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
        implicit ev: ApplicativeAsk[IO, TraceId]
      ): IO[Option[Instance]] = IO.pure(None)
    }
    val dataprocService = new BaseFakeGoogleDataprocService {
      override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
        implicit ev: ApplicativeAsk[IO, TraceId]
      ): IO[Option[Cluster]] = IO.pure(None)
    }

    val runtimeCheckerDeps =
      initRuntimeCheckerDeps(googleComputeService = computeService, googleDataprocService = dataprocService)

    forAll { (runtime: Runtime, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getDeletedRuntimes: fs2.Stream[IO, Runtime] = Stream.emit(runtime)
      }
      val deletedRuntimeChecker = DeletedRuntimeChecker.iml(dbReader, runtimeCheckerDeps)
      val res = deletedRuntimeChecker.checkRuntimeStatus(runtime, dryRun)
      res.unsafeRunSync() shouldBe None
    }
  }

  it should "return Runtime if runtime still exists in Google" in {
    forAll { (runtime: Runtime, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getDeletedRuntimes: fs2.Stream[IO, Runtime] = Stream.emit(runtime)
      }
      val computeService = new FakeGoogleComputeService {
        override def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
          implicit ev: ApplicativeAsk[IO, TraceId]
        ): IO[Option[Instance]] = {
          val instance = Instance.newBuilder().build()
          IO.pure(Some(instance))
        }

        override def deleteInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(
          implicit ev: ApplicativeAsk[IO, TraceId]
        ): IO[Option[Operation]] = if (dryRun) IO.raiseError(fail("this shouldn't be called")) else IO.pure(None)
      }
      val dataprocService = new BaseFakeGoogleDataprocService {
        override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
          implicit ev: ApplicativeAsk[IO, TraceId]
        ): IO[Option[Cluster]] = {
          val cluster = Cluster.newBuilder().build()
          IO.pure(Some(cluster))
        }

        override def deleteCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
          implicit ev: ApplicativeAsk[IO, TraceId]
        ): IO[Option[ClusterOperationMetadata]] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called")) else IO.pure(None)
      }

      val runtimeCheckerDeps =
        initRuntimeCheckerDeps(googleComputeService = computeService, googleDataprocService = dataprocService)

      val deletedRuntimeChecker = DeletedRuntimeChecker.iml(dbReader, runtimeCheckerDeps)
      val res = deletedRuntimeChecker.checkRuntimeStatus(runtime, dryRun)
      res.unsafeRunSync() shouldBe Some(runtime)
    }
  }

  def initRuntimeCheckerDeps(googleComputeService: GoogleComputeService[IO] = FakeGoogleComputeService,
                             googleStorageService: GoogleStorageService[IO] = FakeGoogleStorageInterpreter,
                             googleDataprocService: GoogleDataprocService[IO] = FakeGoogleDataprocService) =
    RuntimeCheckerDeps(
      config.reportDestinationBucket,
      googleComputeService,
      googleStorageService,
      googleDataprocService
    )
}

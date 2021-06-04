package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.IO
import cats.mtl.Ask
import com.broadinstitute.dsp.Generators._
import com.google.cloud.compute.v1.Instance
import com.google.cloud.dataproc.v1.ClusterStatus.State
import com.google.cloud.dataproc.v1.{Cluster, ClusterStatus}
import fs2.Stream
import org.broadinstitute.dsde.workbench.google2.mock.{
  BaseFakeGoogleDataprocService,
  FakeGoogleBillingInterpreter,
  FakeGoogleComputeService,
  FakeGoogleDataprocService,
  FakeGoogleStorageInterpreter
}
import org.broadinstitute.dsde.workbench.google2.{
  DataprocClusterName,
  GoogleBillingService,
  GoogleComputeService,
  GoogleDataprocService,
  GoogleStorageService,
  InstanceName,
  RegionName,
  ZoneName
}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.scalatest.flatspec.AnyFlatSpec
import org.broadinstitute.dsde.workbench.openTelemetry.FakeOpenTelemetryMetricsInterpreter

class DeletedOrErroredRuntimeCheckerSpec extends AnyFlatSpec with CronJobsTestSuite {
  it should "report a runtime if it doesn't exist in google but is still active in leonardo DB" in {
    forAll { (runtime: Runtime, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getRuntimeCandidate: Stream[IO, Runtime] =
          Stream.emit(runtime)
        override def markRuntimeDeleted(id: Long): IO[Unit] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called in dryRun mode")) else IO.unit

        override def insertClusterError(clusterId: Long, errorCode: Option[Int], errorMessage: String): IO[Unit] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called in dryRun mode"))
          else IO(errorCode shouldBe None)
      }
      val computeService = new FakeGoogleComputeService {
        override def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
          ev: Ask[IO, TraceId]
        ): IO[Option[Instance]] = IO.pure(None)
      }
      val dataprocService = new BaseFakeGoogleDataprocService {
        override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
          ev: Ask[IO, TraceId]
        ): IO[Option[Cluster]] = IO.pure(None)
      }
      val deps = initRuntimeCheckerDeps(computeService, dataprocService)
      val checker = DeletedOrErroredRuntimeChecker.impl(dbReader, deps)
      val res = checker.checkResource(runtime, dryRun)
      res.unsafeRunSync() shouldBe Some(runtime)
    }
  }

  it should "not a report runtime if it still exists in google and is active in leonardo DB" in {
    forAll { (runtime: Runtime, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getRuntimeCandidate: Stream[IO, Runtime] =
          Stream.emit(runtime)
        override def markRuntimeDeleted(id: Long): IO[Unit] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called in dryRun mode")) else IO.unit
      }
      val computeService = new FakeGoogleComputeService {
        override def getInstance(project: GoogleProject, zone: ZoneName, instanceName: InstanceName)(implicit
          ev: Ask[IO, TraceId]
        ): IO[Option[Instance]] = IO.pure(Some(Instance.newBuilder().setStatus("Running").build()))
      }
      val dataprocService = new BaseFakeGoogleDataprocService {
        override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
          ev: Ask[IO, TraceId]
        ): IO[Option[Cluster]] =
          IO.pure(
            Some(Cluster.newBuilder().setStatus(ClusterStatus.newBuilder().setState(State.RUNNING).build()).build())
          )
      }
      val deps = initRuntimeCheckerDeps(computeService, dataprocService)
      val checker = DeletedOrErroredRuntimeChecker.impl(dbReader, deps)
      val res = checker.checkResource(runtime, dryRun)
      res.unsafeRunSync() shouldBe None
    }
  }

  it should "report a runtime if it still exists in google in ERROR and is active in leonardo DB" in {
    implicit val arbA = arbDataprocRuntime
    forAll { (runtime: Runtime.Dataproc, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getRuntimeCandidate: Stream[IO, Runtime] =
          Stream.emit(runtime)
        override def updateRuntimeStatus(id: Long, status: String): IO[Unit] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called in dryRun mode")) else IO.unit

        override def insertClusterError(clusterId: Long, errorCode: Option[Int], errorMessage: String): IO[Unit] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called in dryRun mode"))
          else IO(errorCode shouldBe (Some(3)))
      }
      val dataprocService = new BaseFakeGoogleDataprocService {
        override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
          ev: Ask[IO, TraceId]
        ): IO[Option[Cluster]] = IO.pure(
          Some(Cluster.newBuilder().setStatus(ClusterStatus.newBuilder().setState(State.ERROR).build()).build())
        )
      }
      val deps = initRuntimeCheckerDeps(googleDataprocService = dataprocService)
      val checker = DeletedOrErroredRuntimeChecker.impl(dbReader, deps)
      val res = checker.checkResource(runtime, dryRun)
      res.unsafeRunSync() shouldBe Some(runtime)
    }
  }

  it should "mark cluster as Deleted if billing is disabled" in {
    implicit val arbA = arbDataprocRuntime
    forAll { (runtime: Runtime.Dataproc, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getRuntimeCandidate: Stream[IO, Runtime] =
          Stream.emit(runtime)
        override def updateRuntimeStatus(id: Long, status: String): IO[Unit] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called in dryRun mode"))
          else IO.unit

        override def markRuntimeDeleted(id: Long): IO[Unit] =
          IO.raiseError(fail("this shouldn't be called in dryRun mode"))

        override def insertClusterError(clusterId: Long, errorCode: Option[Int], errorMessage: String): IO[Unit] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called in dryRun mode"))
          else IO(errorMessage shouldBe "\nBilling is disabled for this project\n")
      }
      val dataprocService = new BaseFakeGoogleDataprocService {
        override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(implicit
          ev: Ask[IO, TraceId]
        ): IO[Option[Cluster]] = IO.pure(
          Some(Cluster.newBuilder().setStatus(ClusterStatus.newBuilder().setState(State.ERROR).build()).build())
        )
      }
      val billingService = new FakeGoogleBillingInterpreter {
        override def isBillingEnabled(project: GoogleProject)(implicit ev: Ask[IO, TraceId]): IO[Boolean] =
          IO.pure(false)
      }
      val deps = initRuntimeCheckerDeps(googleDataprocService = dataprocService, googleBillingService = billingService)
      val checker = DeletedOrErroredRuntimeChecker.impl(dbReader, deps)
      val res = checker.checkResource(runtime, dryRun)
      res.unsafeRunSync() shouldBe Some(runtime)
    }
  }

  def initRuntimeCheckerDeps(
    googleComputeService: GoogleComputeService[IO] = FakeGoogleComputeService,
    googleDataprocService: GoogleDataprocService[IO] = FakeGoogleDataprocService,
    googleStorageService: GoogleStorageService[IO] = FakeGoogleStorageInterpreter,
    googleBillingService: GoogleBillingService[IO] = FakeGoogleBillingInterpreter
  ): RuntimeCheckerDeps[IO] = {
    val config = Config.appConfig.toOption.get

    RuntimeCheckerDeps(
      googleComputeService,
      googleDataprocService,
      CheckRunnerDeps(config.reportDestinationBucket, googleStorageService, FakeOpenTelemetryMetricsInterpreter),
      googleBillingService
    )
  }
}

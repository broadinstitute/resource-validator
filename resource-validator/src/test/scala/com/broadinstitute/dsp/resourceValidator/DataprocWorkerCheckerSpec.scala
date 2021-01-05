package com.broadinstitute.dsp.resourceValidator

import cats.effect.IO
import cats.mtl.Ask
import com.broadinstitute.dsp.{CronJobsTestSuite, RuntimeWithWorkers}
import com.broadinstitute.dsp.resourceValidator.InitDependenciesHelper.initRuntimeCheckerDeps
import com.google.cloud.dataproc.v1.{Cluster, ClusterConfig, InstanceGroupConfig}
import org.broadinstitute.dsde.workbench.google2.{DataprocClusterName, RegionName}
import org.broadinstitute.dsde.workbench.google2.mock.BaseFakeGoogleDataprocService
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.scalatest.flatspec.AnyFlatSpec
import com.broadinstitute.dsp.Generators._

class DataprocWorkerCheckerSpec extends AnyFlatSpec with CronJobsTestSuite {

  //we don't want to duplicate the purpose of the deleted runtime checker here
  it should "return None if cluster doesn't exist in google" in {
    val dataprocService = new BaseFakeGoogleDataprocService {
      override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
        implicit ev: Ask[IO, TraceId]
      ): IO[Option[Cluster]] = IO.pure(None)
    }

    val runtimeCheckerDeps =
      initRuntimeCheckerDeps(googleDataprocService = dataprocService)

    forAll { (runtime: RuntimeWithWorkers, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getRuntimesWithWorkers: fs2.Stream[IO, RuntimeWithWorkers] = fs2.Stream.emit(runtime)
      }

      val dataprocWorkerChecker = DataprocWorkerChecker.impl(dbReader, runtimeCheckerDeps)
      val res = dataprocWorkerChecker.checkResource(runtime, dryRun)
      res.unsafeRunSync() shouldBe None
    }
  }

  it should "return None if cluster in google worker numbers match the db" in {
    forAll { (runtime: RuntimeWithWorkers, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getRuntimesWithWorkers: fs2.Stream[IO, RuntimeWithWorkers] = fs2.Stream.emit(runtime)
      }

      val dataprocService = new BaseFakeGoogleDataprocService {
        override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
          implicit ev: Ask[IO, TraceId]
        ): IO[Option[Cluster]] =
          IO.pure(
            Some(
              Cluster
                .newBuilder()
                .setConfig(
                  ClusterConfig
                    .newBuilder()
                    .setWorkerConfig(
                      InstanceGroupConfig
                        .newBuilder()
                        .setNumInstances(runtime.workerConfig.numberOfWorkers.getOrElse(0))
                    )
                    .setSecondaryWorkerConfig(
                      InstanceGroupConfig
                        .newBuilder()
                        .setNumInstances(runtime.workerConfig.numberOfPreemptibleWorkers.getOrElse(0))
                    )
                )
                .build()
            )
          )
      }

      val runtimeCheckerDeps =
        initRuntimeCheckerDeps(googleDataprocService = dataprocService)

      val dataprocWorkerChecker = DataprocWorkerChecker.impl(dbReader, runtimeCheckerDeps)
      val res = dataprocWorkerChecker.checkResource(runtime, dryRun)
      res.unsafeRunSync() shouldBe None
    }
  }

  it should "return the runtime if cluster in google primary and secondary worker numbers don't match the db" in {
    forAll { (runtime: RuntimeWithWorkers, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getRuntimesWithWorkers: fs2.Stream[IO, RuntimeWithWorkers] = fs2.Stream.emit(runtime)
      }

      val dataprocService = new BaseFakeGoogleDataprocService {
        override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
          implicit ev: Ask[IO, TraceId]
        ): IO[Option[Cluster]] =
          IO.pure(
            Some(
              Cluster
                .newBuilder()
                .setConfig(
                  ClusterConfig
                    .newBuilder()
                    .setWorkerConfig(InstanceGroupConfig.newBuilder().setNumInstances(0))
                    .setSecondaryWorkerConfig(InstanceGroupConfig.newBuilder().setNumInstances(0))
                )
                .build()
            )
          )
      }

      val runtimeCheckerDeps =
        initRuntimeCheckerDeps(googleDataprocService = dataprocService)

      val dataprocWorkerChecker = DataprocWorkerChecker.impl(dbReader, runtimeCheckerDeps)
      val res = dataprocWorkerChecker.checkResource(runtime, dryRun)
      res.unsafeRunSync() shouldBe Some(runtime)
    }
  }

  it should "return the runtime if cluster in google primary worker numbers don't match the db" in {
    forAll { (runtime: RuntimeWithWorkers, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getRuntimesWithWorkers: fs2.Stream[IO, RuntimeWithWorkers] = fs2.Stream.emit(runtime)
      }

      val dataprocService = new BaseFakeGoogleDataprocService {
        override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
          implicit ev: Ask[IO, TraceId]
        ): IO[Option[Cluster]] =
          IO.pure(
            Some(
              Cluster
                .newBuilder()
                .setConfig(
                  ClusterConfig
                    .newBuilder()
                    .setWorkerConfig(InstanceGroupConfig.newBuilder().setNumInstances(0))
                    .setSecondaryWorkerConfig(
                      InstanceGroupConfig
                        .newBuilder()
                        .setNumInstances(runtime.workerConfig.numberOfPreemptibleWorkers.getOrElse(0))
                    )
                )
                .build()
            )
          )
      }

      val runtimeCheckerDeps =
        initRuntimeCheckerDeps(googleDataprocService = dataprocService)

      val dataprocWorkerChecker = DataprocWorkerChecker.impl(dbReader, runtimeCheckerDeps)
      val res = dataprocWorkerChecker.checkResource(runtime, dryRun)
      res.unsafeRunSync() shouldBe Some(runtime)
    }
  }

  it should "return the runtime if cluster in google secondary worker numbers don't match the db" in {
    forAll { (runtime: RuntimeWithWorkers, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getRuntimesWithWorkers: fs2.Stream[IO, RuntimeWithWorkers] = fs2.Stream.emit(runtime)
      }

      val dataprocService = new BaseFakeGoogleDataprocService {
        override def getCluster(project: GoogleProject, region: RegionName, clusterName: DataprocClusterName)(
          implicit ev: Ask[IO, TraceId]
        ): IO[Option[Cluster]] =
          IO.pure(
            Some(
              Cluster
                .newBuilder()
                .setConfig(
                  ClusterConfig
                    .newBuilder()
                    .setWorkerConfig(
                      InstanceGroupConfig
                        .newBuilder()
                        .setNumInstances(runtime.workerConfig.numberOfWorkers.getOrElse(0))
                    )
                    .setSecondaryWorkerConfig(InstanceGroupConfig.newBuilder().setNumInstances(0))
                )
                .build()
            )
          )
      }

      val runtimeCheckerDeps =
        initRuntimeCheckerDeps(googleDataprocService = dataprocService)

      val dataprocWorkerChecker = DataprocWorkerChecker.impl(dbReader, runtimeCheckerDeps)
      val res = dataprocWorkerChecker.checkResource(runtime, dryRun)
      res.unsafeRunSync() shouldBe Some(runtime)
    }
  }
}

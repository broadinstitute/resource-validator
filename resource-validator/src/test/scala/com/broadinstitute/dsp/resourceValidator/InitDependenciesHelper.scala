package com.broadinstitute.dsp.resourceValidator

import cats.effect.IO
import com.broadinstitute.dsp.{CheckRunnerDeps, KubernetesClusterCheckerDeps, NodepoolCheckerDeps, RuntimeCheckerDeps}
import org.broadinstitute.dsde.workbench.google2.{
  GKEService,
  GoogleComputeService,
  GoogleDataprocService,
  GooglePublisher,
  GoogleStorageService
}
import org.broadinstitute.dsde.workbench.google2.mock.{
  FakeGoogleComputeService,
  FakeGoogleDataprocService,
  FakeGooglePublisher,
  FakeGoogleStorageInterpreter,
  MockGKEService
}
import org.broadinstitute.dsde.workbench.openTelemetry.FakeOpenTelemetryMetricsInterpreter

object InitDependenciesHelper {
  val config = Config.appConfig.toOption.get

  def initRuntimeCheckerDeps(googleComputeService: GoogleComputeService[IO] = FakeGoogleComputeService,
                             googleStorageService: GoogleStorageService[IO] = FakeGoogleStorageInterpreter,
                             googleDataprocService: GoogleDataprocService[IO] = FakeGoogleDataprocService) =
    RuntimeCheckerDeps(
      googleComputeService,
      googleDataprocService,
      CheckRunnerDeps(config.reportDestinationBucket, googleStorageService, FakeOpenTelemetryMetricsInterpreter)
    )

  def initKubernetesClusterCheckerDeps(gkeService: GKEService[IO] = MockGKEService,
                                       googleStorageService: GoogleStorageService[IO] = FakeGoogleStorageInterpreter) =
    KubernetesClusterCheckerDeps(
      CheckRunnerDeps(config.reportDestinationBucket, googleStorageService, FakeOpenTelemetryMetricsInterpreter),
      gkeService
    )

  def initNodepoolCheckerDeps(gkeService: GKEService[IO] = MockGKEService,
                              googleStorageService: GoogleStorageService[IO] = FakeGoogleStorageInterpreter,
                              publisher: GooglePublisher[IO] = new FakeGooglePublisher) =
    NodepoolCheckerDeps(
      CheckRunnerDeps(config.reportDestinationBucket, googleStorageService, FakeOpenTelemetryMetricsInterpreter),
      gkeService,
      publisher
    )
}

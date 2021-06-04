package com.broadinstitute.dsp.resourceValidator

import cats.effect.IO
import com.broadinstitute.dsp.{CheckRunnerDeps, KubernetesClusterCheckerDeps, NodepoolCheckerDeps, RuntimeCheckerDeps}
import org.broadinstitute.dsde.workbench.google2.mock._
import org.broadinstitute.dsde.workbench.google2.{
  GKEService,
  GoogleBillingService,
  GoogleComputeService,
  GoogleDataprocService,
  GooglePublisher,
  GoogleStorageService
}
import org.broadinstitute.dsde.workbench.openTelemetry.FakeOpenTelemetryMetricsInterpreter

object InitDependenciesHelper {
  val config = Config.appConfig.toOption.get

  def initRuntimeCheckerDeps(googleComputeService: GoogleComputeService[IO] = FakeGoogleComputeService,
                             googleStorageService: GoogleStorageService[IO] = FakeGoogleStorageInterpreter,
                             googleDataprocService: GoogleDataprocService[IO] = FakeGoogleDataprocService,
                             googleBillingService: GoogleBillingService[IO] = FakeGoogleBillingInterpreter
  ) =
    RuntimeCheckerDeps(
      googleComputeService,
      googleDataprocService,
      CheckRunnerDeps(config.reportDestinationBucket, googleStorageService, FakeOpenTelemetryMetricsInterpreter),
      googleBillingService
    )

  def initKubernetesClusterCheckerDeps(gkeService: GKEService[IO] = MockGKEService,
                                       googleStorageService: GoogleStorageService[IO] = FakeGoogleStorageInterpreter
  ) =
    KubernetesClusterCheckerDeps(
      CheckRunnerDeps(config.reportDestinationBucket, googleStorageService, FakeOpenTelemetryMetricsInterpreter),
      gkeService
    )

  def initNodepoolCheckerDeps(gkeService: GKEService[IO] = MockGKEService,
                              googleStorageService: GoogleStorageService[IO] = FakeGoogleStorageInterpreter,
                              publisher: GooglePublisher[IO] = new FakeGooglePublisher
  ) =
    NodepoolCheckerDeps(
      CheckRunnerDeps(config.reportDestinationBucket, googleStorageService, FakeOpenTelemetryMetricsInterpreter),
      gkeService,
      publisher
    )
}

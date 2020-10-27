package com.broadinstitute.dsp.resourceValidator

import cats.effect.IO
import com.broadinstitute.dsp.{CheckRunnerDeps, RuntimeCheckerDeps}
import org.broadinstitute.dsde.workbench.google2.{GoogleComputeService, GoogleDataprocService, GoogleStorageService}
import org.broadinstitute.dsde.workbench.google2.mock.{
  FakeGoogleComputeService,
  FakeGoogleDataprocService,
  FakeGoogleStorageInterpreter
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
}

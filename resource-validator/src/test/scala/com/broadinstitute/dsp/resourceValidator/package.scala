package com.broadinstitute.dsp

import cats.effect.IO
import org.broadinstitute.dsde.workbench.google2.mock.{
  FakeGoogleComputeService,
  FakeGoogleDataprocService,
  FakeGoogleStorageInterpreter
}
import org.broadinstitute.dsde.workbench.google2.{GoogleComputeService, GoogleDataprocService, GoogleStorageService}

package object resourceValidator {
  val config = Config.appConfig.toOption.get

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

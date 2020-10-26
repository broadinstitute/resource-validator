package com.broadinstitute.dsp

import java.nio.file.{Path, Paths}

import cats.implicits._
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName
import pureconfig.ConfigReader
import pureconfig.error.{ExceptionThrown, FailureReason}

object ConfigImplicits {
  implicit val pathConfigReader: ConfigReader[Path] =
    ConfigReader.fromString(s => Either.catchNonFatal(Paths.get(s)).leftMap(err => ExceptionThrown(err)))

  implicit val gcsBucketNameReader: ConfigReader[GcsBucketName] =
    ConfigReader.fromString(s => GcsBucketName(s).asRight[FailureReason])
}

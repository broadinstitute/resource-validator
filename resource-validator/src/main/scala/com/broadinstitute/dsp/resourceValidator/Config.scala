package com.broadinstitute.dsp
package resourceValidator

import java.nio.file.Path

import cats.implicits._
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}
import pureconfig._
import pureconfig.generic.auto._
import ConfigImplicits._
import pureconfig.error.FailureReason

object Config {
  implicit val googleProjectReader: ConfigReader[GoogleProject] =
    ConfigReader.fromString(s => GoogleProject(s).asRight[FailureReason])

  val appConfig = ConfigSource.default
    .load[AppConfig]
    .leftMap(failures => new RuntimeException(failures.toList.map(_.description).mkString("\n")))
}

final case class PubsubConfig(googleProject: GoogleProject, topicName: String)
final case class AppConfig(database: DatabaseConfig,
                           pathToCredential: Path,
                           reportDestinationBucket: GcsBucketName,
                           runtimeCheckerConfig: RuntimeCheckerConfig,
                           leonardoPubsub: PubsubConfig)

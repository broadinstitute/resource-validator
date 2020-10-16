package com.broadinstitute.dsp

import java.nio.file.{Path, Paths}

import cats.implicits._
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}
import pureconfig._
import pureconfig.error.{ExceptionThrown, FailureReason}
import pureconfig.generic.auto._

object Config {
  implicit val pathConfigReader: ConfigReader[Path] =
    ConfigReader.fromString(s => Either.catchNonFatal(Paths.get(s)).leftMap(err => ExceptionThrown(err)))

  implicit val googleProjectReader: ConfigReader[GoogleProject] =
    ConfigReader.fromString(s => GoogleProject(s).asRight[FailureReason])

  implicit val gcsBucketNameReader: ConfigReader[GcsBucketName] =
    ConfigReader.fromString(s => GcsBucketName(s).asRight[FailureReason])

  val appConfig = ConfigSource.default
    .load[AppConfig]
    .leftMap(failures => new RuntimeException(failures.toList.map(_.description).mkString("\n")))
}

final case class DatabaseConfig(url: String, user: String, password: String)
final case class PubsubConfig(googleProject: GoogleProject, topicName: String)
final case class AppConfig(database: DatabaseConfig,
                           pathToCredential: Path,
                           reportDestinationBucket: GcsBucketName,
                           leonardoPubsub: PubsubConfig)

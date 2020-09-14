package com.broadinstitute.dsp.resourcevalidator

import java.nio.file.{Path, Paths}

import cats.implicits._
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName
import pureconfig._
import pureconfig.error.ExceptionThrown
import pureconfig.generic.auto._

object Config {
  implicit val pathConfigReader: ConfigReader[Path] =
    ConfigReader.fromString(s => Either.catchNonFatal(Paths.get(s)).leftMap(err => ExceptionThrown(err)))

  implicit val gcsBucketNameReader: ConfigReader[GcsBucketName] =
    ConfigReader.fromString(s => Either.catchNonFatal(GcsBucketName(s)).leftMap(err => ExceptionThrown(err)))

  val appConfig = ConfigSource.default
    .load[AppConfig]
    .leftMap(failures => new RuntimeException(failures.toList.map(_.description).mkString("\n")))
}

final case class DatabaseConfig(url: String, user: String, password: String)
final case class AppConfig(database: DatabaseConfig, pathToCredential: Path, reportDestinationBucket: GcsBucketName)

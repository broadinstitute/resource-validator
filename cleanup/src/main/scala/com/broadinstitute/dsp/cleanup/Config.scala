package com.broadinstitute.dsp
package cleanup

import java.nio.file.Path

import cats.implicits._
import pureconfig._
import pureconfig.generic.auto._

object Config {

  val appConfig = ConfigSource.default
    .load[AppConfig]
    .leftMap(failures => new RuntimeException(failures.toList.map(_.description).mkString("\n")))
}

final case class AppConfig(pubsubTopicCleaner: PubsubTopicCleanerConfig, pathToCredential: Path)

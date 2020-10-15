package com.broadinstitute.dsp

import java.nio.file.{Path, Paths}

import cats.implicits._
import com.google.pubsub.v1.ProjectTopicName
import fs2.Stream
import org.broadinstitute.dsde.workbench.google2.{MaxRetries, PublisherConfig, SubscriberConfig}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}
import pureconfig._
import pureconfig.error.ExceptionThrown
import pureconfig.generic.auto._

import scala.concurrent.duration.FiniteDuration

object Config {
  implicit val pathConfigReader: ConfigReader[Path] =
    ConfigReader.fromString(s => Either.catchNonFatal(Paths.get(s)).leftMap(err => ExceptionThrown(err)))

  implicit val gcsBucketNameReader: ConfigReader[GcsBucketName] =
    ConfigReader.fromString(s => Either.catchNonFatal(GcsBucketName(s)).leftMap(err => ExceptionThrown(err)))

  val appConfig = ConfigSource.default
    .load[AppConfig]
    .leftMap(failures => new RuntimeException(failures.toList.map(_.description).mkString("\n")))

  val pubsubConfig = ConfigSource.default
    .load[PubsubConfig]
    .leftMap(failures => new RuntimeException(failures.toList.map(_.description).mkString("\n")))

  val topic = for {
    pubsubCfg <- Stream.fromEither(Config.pubsubConfig)
  } yield ProjectTopicName.of(pubsubCfg.pubsubGoogleProject.value, pubsubCfg.topicName)

  val publisherConfig: PublisherConfig =
    PublisherConfig(appConfig, topic, retryConfig)

  val subscriberConfig: SubscriberConfig = SubscriberConfig(applicationConfig.leoServiceAccountJsonFile.toString,
    topic,
    config.as[FiniteDuration]("pubsub.ackDeadLine"),
    MaxRetries(10),
    None)

}

final case class DatabaseConfig(url: String, user: String, password: String)
final case class AppConfig(database: DatabaseConfig, pathToCredential: Path, reportDestinationBucket: GcsBucketName)
final case class PubsubConfig(
                               pubsubGoogleProject: GoogleProject,
                               topicName: String,
                               queueSize: Int
                             )

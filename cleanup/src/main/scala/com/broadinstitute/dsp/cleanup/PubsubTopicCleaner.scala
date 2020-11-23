package com.broadinstitute.dsp
package cleanup

import java.util.concurrent.TimeUnit

import cats.effect.{Sync, Timer}
import cats.mtl.Ask
import com.google.pubsub.v1.TopicName
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.GoogleTopicAdmin
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetrics

class PubsubTopicCleaner[F[_]: Timer](config: PubsubTopicCleanerConfig,
                                      topicAdmin: GoogleTopicAdmin[F],
                                      metrics: OpenTelemetryMetrics[F])(
  implicit F: Sync[F],
  logger: Logger[F]
) {
  def run(isDryRun: Boolean): F[Unit] = {
    val res = for {
      now <- Stream.eval(Timer[F].clock.realTime(TimeUnit.MILLISECONDS))
      traceId = TraceId(s"pubsubTopicCleaner-${now}")
      implicit0(ev: Ask[F, TraceId]) = Ask.const(traceId)
      topic <- topicAdmin.list(config.googleProject)
      topicName = TopicName.parse(topic.getName)
      _ <- if (topicName.getTopic.startsWith("leonardo-pubsub-")) {
        if (isDryRun)
          Stream.eval(logger.info(s"${topicName} will be deleted if run in nonDryRun mode"))
        else Stream.eval(metrics.incrementCounter("pubsubTopicCleaner")) >> topicAdmin.delete(topicName, Some(traceId))
      } else Stream.eval(F.unit)
    } yield ()

    res.compile.drain
  }
}

object PubsubTopicCleaner {
  def apply[F[_]: Timer](config: PubsubTopicCleanerConfig,
                         topicAdmin: GoogleTopicAdmin[F],
                         metrics: OpenTelemetryMetrics[F])(
    implicit F: Sync[F],
    logger: Logger[F]
  ): PubsubTopicCleaner[F] = new PubsubTopicCleaner(config, topicAdmin, metrics)
}

final case class PubsubTopicCleanerConfig(googleProject: GoogleProject)

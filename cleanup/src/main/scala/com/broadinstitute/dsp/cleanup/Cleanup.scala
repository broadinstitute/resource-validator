package com.broadinstitute.dsp
package cleanup

import java.util.UUID

import cats.Parallel
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import cats.mtl.Ask
import com.google.auth.oauth2.ServiceAccountCredentials
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.{GoogleTopicAdmin, GoogleTopicAdminInterpreter}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetrics

object Cleanup {
  def run[F[_]: ConcurrentEffect: Parallel](isDryRun: Boolean,
                                            shouldRunAll: Boolean,
                                            shouldDeletePubsubTopics: Boolean)(
    implicit T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
    implicit val traceId = Ask.const(TraceId(UUID.randomUUID()))

    for {
      config <- Stream.fromEither(Config.appConfig)
      deps <- Stream.resource(initDependencies(config))
      deleteRuntimeCheckerProcess = if (shouldRunAll || shouldDeletePubsubTopics)
        Stream.eval(
          PubsubTopicCleaner(config.pubsubTopicCleaner, deps.topicAdminClient, deps.metrics).run(isDryRun)
        )
      else Stream.empty

      processes = Stream(deleteRuntimeCheckerProcess).covary[F]
      _ <- processes.parJoin(2)
    } yield ExitCode.Success
  }.drain

  private def initDependencies[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig
  ): Resource[F, CleanupDeps[F]] =
    for {
      blocker <- Blocker[F]
      metrics <- OpenTelemetryMetrics.resource(appConfig.pathToCredential, "leonardo-cron-jobs", blocker)
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile[F](appConfig.pathToCredential.toString)
      credential <- Resource.liftF(Sync[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
      topicAdminClient <- GoogleTopicAdmin.fromServiceAccountCrendential(credential,
                                                                         GoogleTopicAdminInterpreter.defaultRetryConfig)
    } yield {
      CleanupDeps(blocker, metrics, topicAdminClient)
    }
}

final case class CleanupDeps[F[_]](
  blocker: Blocker,
  metrics: OpenTelemetryMetrics[F],
  topicAdminClient: GoogleTopicAdmin[F]
)

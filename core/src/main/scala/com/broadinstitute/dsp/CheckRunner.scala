package com.broadinstitute.dsp

import java.nio.charset.Charset
import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.effect.{Concurrent, Timer}
import cats.syntax.all._
import cats.mtl.Ask
import fs2.Stream
import org.typelevel.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{GcsBlobName, GooglePublisher, GoogleStorageService}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetrics

trait CheckRunner[F[_], A] {
  def appName: String

  def configs: CheckRunnerConfigs

  def dependencies: CheckRunnerDeps[F]

  def checkResource(a: A, isDryRun: Boolean)(implicit
    ev: Ask[F, TraceId]
  ): F[Option[A]]

  def resourceToScan: Stream[F, A]

  def run(
    isDryRun: Boolean
  )(implicit timer: Timer[F], F: Concurrent[F], logger: Logger[F], ev: Ask[F, TraceId]): F[Unit] =
    for {
      now <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      blobName =
        if (isDryRun)
          GcsBlobName(s"${appName}/${configs.checkType}/dry-run-${Instant.ofEpochMilli(now)}")
        else GcsBlobName(s"${appName}/${configs.checkType}/action-${Instant.ofEpochMilli(now)}")
      _ <- (resourceToScan
        .parEvalMapUnordered(50)(rt => checkResource(rt, isDryRun).handleErrorWith(_ => F.pure(None)))
        .unNone
        .map(_.toString)
        .evalTap(_ => dependencies.metrics.incrementCounter(s"${appName}/${configs.checkType}"))
        .intersperse("\n")
        .map(_.getBytes(Charset.forName("UTF-8")))
        .flatMap(arrayOfBytes => Stream.emits(arrayOfBytes))
        .through(
          dependencies.storageService.streamUploadBlob(
            dependencies.reportDestinationBucket,
            blobName
          )
        ))
        .compile
        .drain
      blob <- dependencies.storageService.getBlob(dependencies.reportDestinationBucket, blobName).compile.last
      _ <- blob.traverse { b =>
        if (b.getSize == 0L)
          logger.warn(s"${configs.checkType} | Finished check. No action needed.") >> dependencies.storageService
            .removeObject(dependencies.reportDestinationBucket, blobName)
            .compile
            .drain
        else {
          // There's a log-based alert set up in production for "Anomaly detected"
          if (configs.shouldAlert)
            logger.error(
              s"${configs.checkType} | Finished check. Anomaly detected. Check out gs://${dependencies.reportDestinationBucket.value}/${blobName.value} for more details"
            )
          else
            logger.warn(
              s"${configs.checkType} | Finished check. Check out gs://${dependencies.reportDestinationBucket.value}/${blobName.value} for more details"
            )
        }
      }
    } yield ()
}

final case class CheckRunnerConfigs(checkType: String, shouldAlert: Boolean)
final case class CheckRunnerDeps[F[_]](reportDestinationBucket: GcsBucketName,
                                       storageService: GoogleStorageService[F],
                                       metrics: OpenTelemetryMetrics[F]
)
final case class LeoPublisherDeps[F[_]](publisher: GooglePublisher[F], checkRunnerDeps: CheckRunnerDeps[F])

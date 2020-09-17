package com.broadinstitute.dsp

import java.nio.charset.Charset
import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{GcsBlobName, GoogleStorageService}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName

trait CheckRunner[F[_], A] {
  def configs: CheckRunnerConfigs

  def dependencies: CheckRunnerDeps[F]

  def checkA(a: A, isDryRun: Boolean)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[A]]

  def aToScan: Stream[F, A]

  def run(
    isDryRun: Boolean
  )(implicit timer: Timer[F], F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): F[Unit] =
    for {
      now <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      blobName = if (isDryRun)
        GcsBlobName(s"${configs.checkType}/dry-run-${Instant.ofEpochMilli(now)}")
      else GcsBlobName(s"${configs.checkType}/action-${Instant.ofEpochMilli(now)}")
      _ <- (aToScan
        .parEvalMapUnordered(50)(rt => checkA(rt, isDryRun).handleErrorWith(_ => F.pure(None)))
        .unNone
        .map(_.toString)
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
          logger.warn(s"${configs.checkType} | No action needed.") >> dependencies.storageService
            .removeObject(dependencies.reportDestinationBucket, blobName)
            .compile
            .drain
        else {
          // There's log based alert set up in production for "Anomaly detected"
          if (configs.shouldAlert)
            logger.error(
              s"${configs.checkType} | Anomaly detected. Check out gs://${dependencies.reportDestinationBucket.value}/${blobName.value} for more details"
            )
          else
            logger.warn(
              s"${configs.checkType} | Check out gs://${dependencies.reportDestinationBucket.value}/${blobName.value} for more details"
            )
        }
      }
    } yield ()
}

final case class CheckRunnerConfigs(checkType: String, shouldAlert: Boolean)
final case class CheckRunnerDeps[F[_]](reportDestinationBucket: GcsBucketName, storageService: GoogleStorageService[F])

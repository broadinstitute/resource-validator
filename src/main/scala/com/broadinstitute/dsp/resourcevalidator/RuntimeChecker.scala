package com.broadinstitute.dsp.resourcevalidator

import java.nio.charset.Charset
import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{
  GcsBlobName,
  GoogleComputeService,
  GoogleDataprocService,
  GoogleStorageService
}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

// Algebra
trait RuntimeChecker[F[_]] {
  def checkType: String

  def dependencies: RuntimeCheckerDeps[F]

  def checkRuntimeStatus(runtime: Runtime, isDryRun: Boolean)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Runtime]]

  def run(
    isDryRun: Boolean
  )(implicit timer: Timer[F], F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): F[Unit] =
    for {
      now <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      blobName = if (isDryRun)
        GcsBlobName(s"$checkType-dry-run-${Instant.ofEpochMilli(now)}")
      else GcsBlobName(s"$checkType-${Instant.ofEpochMilli(now)}")
      _ <- (dependencies.dbReader.getErroredRuntimes
        .parEvalMapUnordered(50)(rt => checkRuntimeStatus(rt, isDryRun).handleErrorWith(_ => F.pure(None)))
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
          logger.warn(s"${checkType} | No anomaly detected") >> dependencies.storageService
            .removeObject(dependencies.reportDestinationBucket, blobName)
            .compile
            .drain
        else
          logger.error(
            s"${checkType} | Anomaly detected. Check out gs://${dependencies.reportDestinationBucket.value}/${blobName.value} for more details"
          )
      }
    } yield ()
}

sealed abstract class CloudService extends Product with Serializable
object CloudService {
  final case object Gce extends CloudService
  final case object Dataproc extends CloudService
}

final case class Runtime(googleProject: GoogleProject, runtimeName: String, cloudService: CloudService) {
  override def toString: String = s"${googleProject.value},${runtimeName},${cloudService}"
}
final case class RuntimeCheckerDeps[F[_]](reportDestinationBucket: GcsBucketName,
                                          computeService: GoogleComputeService[F],
                                          storageService: GoogleStorageService[F],
                                          dataprocService: GoogleDataprocService[F],
                                          dbReader: DbReader[F])

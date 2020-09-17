package com.broadinstitute.dsp

import java.nio.charset.Charset
import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, Concurrent, ContextShift, Resource, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.google.auth.oauth2.ServiceAccountCredentials
import fs2.Stream
import io.chrisdavenport.log4cats.{Logger, StructuredLogger}
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.google2.{
  GcsBlobName,
  GoogleComputeService,
  GoogleDataprocService,
  GoogleStorageService
}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

import scala.jdk.CollectionConverters._

// Algebra
trait AnomalyChecker[F[_]] {
  def checkType: String

  def dependencies: RuntimeCheckerDeps[F]

  def checkRuntimeStatus(runtime: Runtime, isDryRun: Boolean)(
    implicit ev: ApplicativeAsk[F, TraceId]
  ): F[Option[Runtime]]

  def runtimesToScan: Stream[F, Runtime]

  def run(
    isDryRun: Boolean
  )(implicit timer: Timer[F], F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): F[Unit] =
    for {
      now <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      blobName = if (isDryRun)
        GcsBlobName(s"$checkType/dry-run-${Instant.ofEpochMilli(now)}")
      else GcsBlobName(s"$checkType/action-${Instant.ofEpochMilli(now)}")
      _ <- (runtimesToScan
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

object AnomalyChecker {
  def initRuntimeCheckerDeps[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig,
    blocker: Blocker
  ): Resource[F, RuntimeCheckerDeps[F]] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile[F](appConfig.pathToCredential.toString)
      credential <- Resource.liftF(Async[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
      scopedCredential = credential.createScoped(Seq("https://www.googleapis.com/auth/cloud-platform").asJava)
      blockerBound <- Resource.liftF(Semaphore[F](10))
      computeService <- GoogleComputeService.fromCredential(scopedCredential,
                                                            blocker,
                                                            blockerBound,
                                                            RetryPredicates.standardRetryConfig)
      storageService <- GoogleStorageService.resource(appConfig.pathToCredential.toString,
                                                      blocker,
                                                      Some(blockerBound),
                                                      None)
      dataprocService <- GoogleDataprocService.fromCredential(scopedCredential,
                                                              blocker,
                                                              regionName,
                                                              blockerBound,
                                                              RetryPredicates.standardRetryConfig)
    } yield {
      RuntimeCheckerDeps(appConfig.reportDestinationBucket, computeService, storageService, dataprocService)
    }
}

final case class Runtime(googleProject: GoogleProject, runtimeName: String, cloudService: CloudService) {
  override def toString: String = s"${googleProject.value},${runtimeName},${cloudService}"
}
final case class RuntimeCheckerDeps[F[_]](reportDestinationBucket: GcsBucketName,
                                          computeService: GoogleComputeService[F],
                                          storageService: GoogleStorageService[F],
                                          dataprocService: GoogleDataprocService[F])

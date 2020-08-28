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
  DataprocClusterName,
  GcsBlobName,
  GoogleComputeService,
  GoogleDataprocService,
  GoogleStorageService,
  InstanceName
}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

// Algebra
trait DeletedRuntimeChecker[F[_]] {
  def run(isDryRun: Boolean): F[Unit]
}

// Interpreter
object DeletedRuntimeChecker {
  implicit def apply[F[_]](implicit ev: DeletedRuntimeChecker[F]): DeletedRuntimeChecker[F] = ev

  def iml[F[_]: Timer](
    dependencies: DeletedRuntimeCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): DeletedRuntimeChecker[F] =
    new DeletedRuntimeChecker[F] {
      override def run(isDryRun: Boolean): F[Unit] =
        for {
          now <- Timer[F].clock.realTime(TimeUnit.MILLISECONDS)

          blobName = if (isDryRun) GcsBlobName(s"runtimes-should-have-been-deleted-${Instant.ofEpochMilli(now)}")
          else GcsBlobName(s"runtimes-have-been-deleted-${Instant.ofEpochMilli(now)}")

          _ <- (dependencies.dbReader.getDeletedRuntimes
            .parEvalMapUnordered(50)(rt => checkRuntimeStatus(rt, isDryRun))
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
        } yield ()

      def checkRuntimeStatus(runtime: Runtime,
                             isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Runtime]] =
        runtime.cloudService match {
          case "DATAPROC" =>
            checkClusterStatus(runtime, isDryRun)
          case "GCE" =>
            checkGceRuntimeStatus(runtime, isDryRun)
        }

      def checkClusterStatus(runtime: Runtime,
                             isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Runtime]] =
        for {
          clusterOpt <- dependencies.dataprocService
            .getCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
            .handleErrorWith {
              case e: com.google.api.gax.rpc.PermissionDeniedException => // this could happen if the project no longer exists or managed by terra
                logger.debug(s"${runtime}: Fail to check status due to ${e}").as(None)
              case e =>
                logger.error(s"${runtime}: Fail to check status due to ${e}").as(None)
            }
          _ <- clusterOpt.traverse_ { _ =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in Google. It needs to be deleted")
            else
              logger.warn(s"${runtime} still exists in Google. Going to delete") >> dependencies.dataprocService
                .deleteCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
                .void
                .handleErrorWith {
                  case e =>
                    logger.error(s"Fail to delete ${runtime} due to ${e}")
                }
          }
        } yield clusterOpt.fold(none[Runtime])(_ => Some(runtime))

      def checkGceRuntimeStatus(runtime: Runtime, isDryRun: Boolean): F[Option[Runtime]] =
        for {
          runtimeOpt <- dependencies.computeService
            .getInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
            .handleErrorWith {
              case e: com.google.api.gax.rpc.PermissionDeniedException => // this could happen if the project no longer exists or managed by terra
                logger.debug(s"${runtime}: Fail to check status due to ${e}").as(None)
              case e: com.google.api.gax.rpc.UnknownException => // this could happen if the project no longer exists or managed by terra
                logger.debug(s"${runtime}: Fail to check status due to ${e}").as(None)
              case e =>
                logger.error(s"${runtime}: Fail to check status due to ${e}").as(None)
            }
          _ <- runtimeOpt.traverse_ { _ =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in Google. It needs to be deleted")
            else
              logger.warn(s"${runtime} still exists in Google. Going to delete") >>
                dependencies.computeService
                  .deleteInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
                  .void
                  .handleErrorWith(t => logger.info(s"Fail to delete ${runtime} due to ${t}"))
          }
        } yield runtimeOpt.fold(none[Runtime])(_ => Some(runtime))

    }
}

final case class Runtime(googleProject: GoogleProject, runtimeName: String, cloudService: String) {
  override def toString: String = s"${googleProject.value},${runtimeName},${cloudService}"
}
final case class DeletedRuntimeCheckerDeps[F[_]](reportDestinationBucket: GcsBucketName,
                                                 computeService: GoogleComputeService[F],
                                                 storageService: GoogleStorageService[F],
                                                 dataprocService: GoogleDataprocService[F],
                                                 dbReader: DbReader[F])

package com.broadinstitute.dsp.resourcevalidator

import cats.effect.Concurrent
import cats.implicits._
import com.google.api.core.{ApiFutureCallback, ApiFutures}
import com.google.cloud.compute.v1.{InstanceClient, ProjectZoneInstanceName}
import com.google.cloud.dataproc.v1.{ClusterControllerClient, ClusterOperationMetadata}
import com.google.common.util.concurrent.MoreExecutors
import io.chrisdavenport.log4cats.Logger

// Algebra
trait DeletedRuntimeChecker[F[_]] {
  def run(isDryRun: Boolean): F[Unit]
}

// Interpreter
object DeletedRuntimeChecker {
  implicit def apply[F[_]](implicit ev: DeletedRuntimeChecker[F]): DeletedRuntimeChecker[F] = ev

  def iml[F[_]](dependencies: DeletedRuntimeCheckerDeps[F])(implicit F: Concurrent[F],
                                                            logger: Logger[F]): DeletedRuntimeChecker[F] =
    new DeletedRuntimeChecker[F] {
      override def run(isDryRun: Boolean): F[Unit] =
        dependencies.dbReader.getDeletedRuntimes
          .parEvalMapUnordered(50)(rt => checkRuntimeStatus(rt, isDryRun))
          //          .take(1000)
          .compile
          .drain //TODO: remove this take

      def checkRuntimeStatus(runtime: Runtime, isDryRun: Boolean): F[Unit] =
        runtime.cloudService match {
          case "DATAPROC" =>
            checkClusterStatus(runtime, isDryRun)
          case "GCE" =>
            checkGceRuntimeStatus(runtime, isDryRun)
        }

      def checkClusterStatus(runtime: Runtime, isDryRun: Boolean): F[Unit] =
        for {
          clusterOpt <- F
            .delay(dependencies.dataprocClient.getCluster(runtime.googleProject, regionName, runtime.runtimeName))
            .map(x => Option(x))
            .handleErrorWith {
              case _: com.google.api.gax.rpc.NotFoundException =>
                logger.debug(s"${runtime}: Doesn't exist in Google").as(None)
              case e: com.google.api.gax.rpc.PermissionDeniedException => // this could happen if the project no longer exists or managed by terra
                logger.debug(s"${runtime}: Fail to check status due to ${e}").as(None)
              case e =>
                logger.error(s"${runtime}: Fail to check status due to ${e}").as(None)
            }
          _ <- clusterOpt.traverse_ { _ =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in Google. It needs to be deleted")
            else
              logger.warn(s"${runtime} still exists in Google. Going to delete") >> F
                .async[ClusterOperationMetadata] { cb =>
                  ApiFutures.addCallback(
                    dependencies.dataprocClient
                      .deleteClusterAsync(runtime.googleProject, regionName, runtime.runtimeName)
                      .getMetadata,
                    new ApiFutureCallback[ClusterOperationMetadata] {
                      @Override def onFailure(t: Throwable): Unit = cb(Left(t))

                      @Override def onSuccess(result: ClusterOperationMetadata): Unit =
                        cb(Right(result))
                    },
                    MoreExecutors.directExecutor()
                  )
                }
                .void
                .handleErrorWith {
                  case e =>
                    logger.error(s"Fail to delete ${runtime} due to ${e}")
                }
          }
        } yield ()

      def checkGceRuntimeStatus(runtime: Runtime, isDryRun: Boolean): F[Unit] = {
        val instance = ProjectZoneInstanceName.of(runtime.runtimeName, runtime.googleProject, zoneName)
        for {
          runtimeOpt <- F.delay(dependencies.instanceClient.getInstance(instance)).map(x => Option(x)).handleErrorWith {
            case _: com.google.api.gax.rpc.NotFoundException =>
              logger.debug(s"${runtime}: Doesn't exist in Google").as(None)
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
              logger.warn(s"${runtime} still exists in Google. Going to delete") >> F
                .delay(
                  dependencies.instanceClient.deleteInstance(instance)
                )
                .void
                .handleErrorWith(t => logger.info(s"Fail to delete ${runtime} due to ${t}"))
          }
        } yield ()
      }

    }
}

final case class Runtime(googleProject: String, runtimeName: String, cloudService: String)
final case class DeletedRuntimeCheckerDeps[F[_]](dataprocClient: ClusterControllerClient,
                                                 instanceClient: InstanceClient,
                                                 dbReader: DbReader[F])

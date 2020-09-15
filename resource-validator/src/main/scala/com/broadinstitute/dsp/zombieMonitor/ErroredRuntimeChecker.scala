package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{DataprocClusterName, InstanceName}
import org.broadinstitute.dsde.workbench.model.TraceId

// Interpreter
object ErroredRuntimeChecker {
  def iml[F[_]: Timer](
    dbReader: DbReader[F],
    deps: RuntimeCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): RuntimeChecker[F] =
    new RuntimeChecker[F] {
      override def checkType = "error-ed-runtime"
      override def dependencies: RuntimeCheckerDeps[F] = deps

      override def checkRuntimeStatus(runtime: Runtime, isDryRun: Boolean)(
        implicit ev: ApplicativeAsk[F, TraceId]
      ): F[Option[Runtime]] = runtime.cloudService match {
        case CloudService.Dataproc =>
          checkDataprocCluster(runtime, isDryRun)
        case CloudService.Gce =>
          checkGceRuntime(runtime, isDryRun)
      }

      def checkDataprocCluster(runtime: Runtime,
                               isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Runtime]] =
        for {
          clusterOpt <- dependencies.dataprocService
            .getCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
          r <- clusterOpt.flatTraverse[F, Runtime] { cluster =>
            if (cluster.getStatus.getState.name() == "ERROR")
              logger
                .warn(s"${runtime} still exists in Google in Error state. User might want to delete the runtime.")
                .as(none[Runtime])
            else {
              if (isDryRun)
                logger
                  .warn(
                    s"${runtime} still exists in ${cluster.getStatus.getState.name()} status. It needs to be deleted."
                  )
                  .as(Some(runtime))
              else
                logger.warn(s"${runtime} still exists in ${cluster.getStatus.getState.name()} status. Going to delete") >> dependencies.dataprocService
                  .deleteCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
                  .void
                  .as(Some(runtime))
            }
          }
        } yield r

      def checkGceRuntime(runtime: Runtime, isDryRun: Boolean): F[Option[Runtime]] =
        for {
          runtimeOpt <- dependencies.computeService
            .getInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
          _ <- runtimeOpt.traverse_ { rt =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in ${rt.getStatus} status. It needs to be deleted.")
            else
              logger.warn(s"${runtime} still exists in ${rt.getStatus} status. Going to delete") >>
                dependencies.computeService
                  .deleteInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
                  .void
          }
        } yield runtimeOpt.fold(none[Runtime])(_ => Some(runtime))

      override def runtimesToScan: fs2.Stream[F, Runtime] = dbReader.getErroredRuntimes
    }
}

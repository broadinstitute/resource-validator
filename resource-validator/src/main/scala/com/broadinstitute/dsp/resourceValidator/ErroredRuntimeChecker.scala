package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.Ask
import com.broadinstitute.dsp.CloudService.{Dataproc, Gce}
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{DataprocClusterName, InstanceName}
import org.broadinstitute.dsde.workbench.model.TraceId

// Implements CheckRunner[F[_], A]
object ErroredRuntimeChecker {
  def iml[F[_]: Timer](
    dbReader: DbReader[F],
    deps: RuntimeCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: Ask[F, TraceId]): CheckRunner[F, Runtime] =
    new CheckRunner[F, Runtime] {
      override def appName: String = resourceValidator.appName
      override def configs = CheckRunnerConfigs(s"errored-runtime", true)
      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps
      override def resourceToScan: fs2.Stream[F, Runtime] = dbReader.getErroredRuntimes

      override def checkResource(runtime: Runtime, isDryRun: Boolean)(
        implicit ev: Ask[F, TraceId]
      ): F[Option[Runtime]] = runtime.cloudService match {
        case Dataproc =>
          checkDataprocCluster(runtime, isDryRun)
        case Gce =>
          checkGceRuntime(runtime, isDryRun)
      }

      def checkDataprocCluster(runtime: Runtime, isDryRun: Boolean)(implicit ev: Ask[F, TraceId]): F[Option[Runtime]] =
        for {
          clusterOpt <- deps.dataprocService
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
                logger.warn(s"${runtime} still exists in ${cluster.getStatus.getState.name()} status. Going to delete") >> deps.dataprocService
                  .deleteCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
                  .void
                  .as(Some(runtime))
            }
          }
        } yield r

      def checkGceRuntime(runtime: Runtime, isDryRun: Boolean): F[Option[Runtime]] =
        for {
          runtimeOpt <- deps.computeService
            .getInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
          _ <- runtimeOpt.traverse_ { rt =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in ${rt.getStatus} status. It needs to be deleted.")
            else
              logger.warn(s"${runtime} still exists in ${rt.getStatus} status. Going to delete") >>
                deps.computeService
                  .deleteInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
                  .void
          }
        } yield runtimeOpt.fold(none[Runtime])(_ => Some(runtime))

    }
}

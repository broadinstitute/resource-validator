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
    deps: BillingDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: Ask[F, TraceId]): CheckRunner[F, Runtime] =
    new CheckRunner[F, Runtime] {
      override def appName: String = resourceValidator.appName
      override def configs = CheckRunnerConfigs(s"errored-runtime", shouldAlert = true)
      override def dependencies: CheckRunnerDeps[F] = deps.runtimeCheckerDeps.checkRunnerDeps
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
          clusterOpt <- deps.runtimeCheckerDeps.dataprocService
            .getCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
          r <- clusterOpt.flatTraverse[F, Runtime] { cluster =>
            for {
              isBillingEnabled <- deps.billingService.isBillingEnabled(runtime.googleProject)
              r <- if (cluster.getStatus.getState.name.toUpperCase == "ERROR")
                logger
                  .warn(s"${runtime} still exists in Google in Error state. User might want to delete the runtime.")
                  .as(none[Runtime])
              else {
                if (isDryRun)
                  logger
                    .warn(
                      s"${runtime} still exists in ${cluster.getStatus.getState.name} status. It needs to be deleted. isBillingEnabled: $isBillingEnabled. Project: ${runtime.googleProject}"
                    )
                    .as(Option(runtime))
                else
                  isBillingEnabled match {
                    case true =>
                      logger.warn(
                        s"${runtime} still exists in ${cluster.getStatus.getState.name} status with billing enabled. Going to delete it."
                      ) >> deps.runtimeCheckerDeps.dataprocService
                        .deleteCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
                        .void
                        .as(Option(runtime))
                    case false =>
                      logger
                        .warn(
                          s"${runtime} still exists with an anomaly, but cannot delete it because billing is disabled."
                        )
                        .as(none[Runtime])
                  }
              }
            } yield r
          }
        } yield r

      def checkGceRuntime(runtime: Runtime, isDryRun: Boolean): F[Option[Runtime]] =
        for {
          runtimeOpt <- deps.runtimeCheckerDeps.computeService
            .getInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
          _ <- runtimeOpt.traverse_ { rt =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in ${rt.getStatus} status. It needs to be deleted.")
            else
              logger.warn(s"${runtime} still exists in ${rt.getStatus} status. Going to delete it.") >>
                deps.runtimeCheckerDeps.computeService
                  .deleteInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
                  .void
          }
        } yield runtimeOpt.fold(none[Runtime])(_ => Some(runtime))

    }
}

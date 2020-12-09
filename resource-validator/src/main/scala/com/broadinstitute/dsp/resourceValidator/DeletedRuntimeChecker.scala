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
object DeletedRuntimeChecker {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: RuntimeCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: Ask[F, TraceId]): CheckRunner[F, Runtime] =
    new CheckRunner[F, Runtime] {
      override def appName: String = resourceValidator.appName

      override def configs = CheckRunnerConfigs(s"deleted-runtime", shouldAlert = true)

      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps

      override def checkResource(runtime: Runtime,
                                 isDryRun: Boolean)(implicit ev: Ask[F, TraceId]): F[Option[Runtime]] =
        runtime.cloudService match {
          case Dataproc =>
            checkDataprocCluster(runtime, isDryRun)
          case Gce =>
            checkGceRuntime(runtime, isDryRun)
        }

      override def resourceToScan: fs2.Stream[F, Runtime] = dbReader.getDeletedRuntimes

      def checkDataprocCluster(runtime: Runtime, isDryRun: Boolean)(
        implicit ev: Ask[F, TraceId]
      ): F[Option[Runtime]] =
        for {
          clusterOpt <- deps.dataprocService
            .getCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
          _ <- clusterOpt.traverse_ { _ =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in Google. It needs to be deleted.")
            else
              logger.warn(s"${runtime} still exists in Google. Going to delete it.") >> deps.dataprocService
                .deleteCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
                .void
          }
        } yield clusterOpt.fold(none[Runtime])(_ => Some(runtime))

      def checkGceRuntime(runtime: Runtime, isDryRun: Boolean): F[Option[Runtime]] =
        for {
          runtimeOpt <- deps.computeService
            .getInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
          _ <- runtimeOpt.traverse_ { _ =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in Google. It needs to be deleted")
            else
              logger.warn(s"${runtime} still exists in Google. Going to delete") >>
                deps.computeService
                  .deleteInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
                  .void
          }
        } yield runtimeOpt.fold(none[Runtime])(_ => Some(runtime))
    }
}

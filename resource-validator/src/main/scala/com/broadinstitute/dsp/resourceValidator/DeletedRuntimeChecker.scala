package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.broadinstitute.dsp
import com.broadinstitute.dsp.CloudService.{Dataproc, Gce}
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{DataprocClusterName, InstanceName}
import org.broadinstitute.dsde.workbench.model.TraceId

// Implements CheckRunner[F[_], A]
object DeletedRuntimeChecker {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: AnomalyCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): CheckRunner[F, Runtime] =
    new CheckRunner[F, Runtime] {
      override def configs = CheckRunnerConfigs("resource-validator-deleted-runtime", true)

      override def dependencies: CheckRunnerDeps[F] = CheckRunnerDeps(deps.reportDestinationBucket, deps.storageService)

      override def checkA(runtime: Runtime,
                          isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[dsp.Runtime]] =
        runtime.cloudService match {
          case Dataproc =>
            checkDataprocClusterStatus(runtime, isDryRun)
          case Gce =>
            checkGceRuntimeStatus(runtime, isDryRun)
        }

      override def aToScan: fs2.Stream[F, dsp.Runtime] = dbReader.getDeletedRuntimes

      def checkDataprocClusterStatus(runtime: dsp.Runtime, isDryRun: Boolean)(
        implicit ev: ApplicativeAsk[F, TraceId]
      ): F[Option[dsp.Runtime]] =
        for {
          clusterOpt <- deps.dataprocService
            .getCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
          _ <- clusterOpt.traverse_ { _ =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in Google. It needs to be deleted")
            else
              logger.warn(s"${runtime} still exists in Google. Going to delete") >> deps.dataprocService
                .deleteCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
                .void
          }
        } yield clusterOpt.fold(none[dsp.Runtime])(_ => Some(runtime))

      def checkGceRuntimeStatus(runtime: dsp.Runtime, isDryRun: Boolean): F[Option[dsp.Runtime]] =
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
        } yield runtimeOpt.fold(none[dsp.Runtime])(_ => Some(runtime))
    }
}
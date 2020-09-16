package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.broadinstitute.dsp
import com.broadinstitute.dsp.CloudService.{Dataproc, Gce}
import com.broadinstitute.dsp.{RuntimeChecker, RuntimeCheckerDeps}
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{DataprocClusterName, InstanceName}
import org.broadinstitute.dsde.workbench.model.TraceId

// Interpreter
object DeletedRuntimeChecker {
  implicit def apply[F[_]](implicit ev: RuntimeChecker[F]): RuntimeChecker[F] = ev

  def iml[F[_]: Timer](
    dbReader: DbReader[F],
    deps: RuntimeCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): RuntimeChecker[F] =
    new RuntimeChecker[F] {
      override def checkType = "deleted-runtime"

      override def dependencies: RuntimeCheckerDeps[F] = deps

      def checkRuntimeStatus(runtime: dsp.Runtime,
                             isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[dsp.Runtime]] =
        runtime.cloudService match {
          case Dataproc =>
            checkClusterStatus(runtime, isDryRun)
          case Gce =>
            checkGceRuntimeStatus(runtime, isDryRun)
        }

      def checkClusterStatus(runtime: dsp.Runtime,
                             isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[dsp.Runtime]] =
        for {
          clusterOpt <- dependencies.dataprocService
            .getCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
          _ <- clusterOpt.traverse_ { _ =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in Google. It needs to be deleted")
            else
              logger.warn(s"${runtime} still exists in Google. Going to delete") >> dependencies.dataprocService
                .deleteCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
                .void
          }
        } yield clusterOpt.fold(none[dsp.Runtime])(_ => Some(runtime))

      def checkGceRuntimeStatus(runtime: dsp.Runtime, isDryRun: Boolean): F[Option[dsp.Runtime]] =
        for {
          runtimeOpt <- dependencies.computeService
            .getInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
          _ <- runtimeOpt.traverse_ { _ =>
            if (isDryRun)
              logger.warn(s"${runtime} still exists in Google. It needs to be deleted")
            else
              logger.warn(s"${runtime} still exists in Google. Going to delete") >>
                dependencies.computeService
                  .deleteInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
                  .void
          }
        } yield runtimeOpt.fold(none[dsp.Runtime])(_ => Some(runtime))

      override def runtimesToScan: fs2.Stream[F, dsp.Runtime] = dbReader.getDeletedRuntimes
    }
}

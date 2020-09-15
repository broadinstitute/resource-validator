package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
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

      def checkRuntimeStatus(runtime: Runtime,
                             isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Runtime]] =
        runtime.cloudService match {
          case CloudService.Dataproc =>
            checkClusterStatus(runtime, isDryRun)
          case CloudService.Gce =>
            checkGceRuntimeStatus(runtime, isDryRun)
        }

      def checkClusterStatus(runtime: Runtime,
                             isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Runtime]] =
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
        } yield clusterOpt.fold(none[Runtime])(_ => Some(runtime))

      def checkGceRuntimeStatus(runtime: Runtime, isDryRun: Boolean): F[Option[Runtime]] =
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
        } yield runtimeOpt.fold(none[Runtime])(_ => Some(runtime))

      override def runtimesToScan: fs2.Stream[F, Runtime] = dbReader.getDeletedRuntimes
    }
}

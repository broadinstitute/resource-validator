package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import com.broadinstitute.dsp.CloudService.{Dataproc, Gce}
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{DataprocClusterName, InstanceName}
import org.broadinstitute.dsde.workbench.model.TraceId

/**
 * Take a `Stream` of active runtimes, and check their status in google
 * 1. if non-existent in google, update DB to "Deleted"
 * 2. if exists but in Error status in google, update DB to "Error"
 * 3. else, do nothing
 */
object DeletedOrErrorRuntimeChecker {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: RuntimeCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): CheckRunner[F, Runtime] =
    new CheckRunner[F, Runtime] {
      override def resourceToScan: Stream[F, Runtime] = dbReader.getRuntimeCandidate
      override def configs = CheckRunnerConfigs(s"deleted-or-errored-runtime", false)
      override def appName: String = zombieMonitor.appName
      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps

      def checkResource(a: Runtime, isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Runtime]] =
        a.cloudService match {
          case Dataproc =>
            checkDataprocClusterStatus(a, isDryRun)
          case Gce =>
            checkGceRuntimeStatus(a, isDryRun)
        }

      def checkDataprocClusterStatus(runtime: Runtime, isDryRun: Boolean)(
        implicit ev: ApplicativeAsk[F, TraceId]
      ): F[Option[Runtime]] =
        for {
          runtimeOpt <- deps.dataprocService
            .getCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))

          res <- runtimeOpt match {
            case None =>
              (if (isDryRun) F.unit
               else
                 dbReader.markRuntimeDeleted(runtime.id)).as(Some(runtime))
            case Some(instance) =>
              if (instance.getStatus.getState == com.google.cloud.dataproc.v1.ClusterStatus.State.ERROR) {
                (if (isDryRun) F.unit
                 else
                   dbReader.updateRuntimeStatus(runtime.id, "Error")).as(Some(runtime))
              } else F.pure(none[Runtime])
          }
        } yield res

      def checkGceRuntimeStatus(runtime: Runtime, isDryRun: Boolean): F[Option[Runtime]] =
        for {
          runtimeOpt <- deps.computeService
            .getInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
          _ <- if (isDryRun) F.unit
          else
            runtimeOpt match {
              case None    => dbReader.markRuntimeDeleted(runtime.id)
              case Some(_) => F.unit
            }
        } yield runtimeOpt.fold[Option[Runtime]](Some(runtime))(_ => none[Runtime])
    }
}

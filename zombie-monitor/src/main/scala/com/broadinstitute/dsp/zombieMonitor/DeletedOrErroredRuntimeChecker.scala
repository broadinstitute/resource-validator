package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.{Concurrent, Timer}
import cats.syntax.all._
import cats.mtl.Ask
import fs2.Stream
import org.typelevel.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{DataprocClusterName, InstanceName}
import org.broadinstitute.dsde.workbench.model.TraceId

/**
 * Take a `Stream` of active runtimes, and check their status in google
 * 1. if non-existent in google, update DB to "Deleted"
 * 2. if exists but in Error status in google, update DB to "Error"
 * 3. else, do nothing
 */
object DeletedOrErroredRuntimeChecker {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: RuntimeCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: Ask[F, TraceId]): CheckRunner[F, Runtime] =
    new CheckRunner[F, Runtime] {
      override def resourceToScan: Stream[F, Runtime] = dbReader.getRuntimeCandidate
      override def configs = CheckRunnerConfigs(s"deleted-or-errored-runtime", false)
      override def appName: String = zombieMonitor.appName
      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps

      def checkResource(a: Runtime, isDryRun: Boolean)(implicit ev: Ask[F, TraceId]): F[Option[Runtime]] =
        a match {
          case x: Runtime.Dataproc =>
            checkDataprocClusterStatus(x, isDryRun)
          case x: Runtime.Gce =>
            checkGceRuntimeStatus(x, isDryRun)
        }

      def checkDataprocClusterStatus(runtime: Runtime.Dataproc, isDryRun: Boolean)(
        implicit ev: Ask[F, TraceId]
      ): F[Option[Runtime]] =
        for {
          runtimeOpt <- deps.dataprocService
            .getCluster(runtime.googleProject, runtime.region, DataprocClusterName(runtime.runtimeName))

          res <- runtimeOpt match {
            case None =>
              (if (isDryRun) F.unit
               else {
                 for {
                   _ <- dbReader.markRuntimeDeleted(runtime.id)
                   _ <- dbReader.insertClusterError(
                     runtime.id,
                     None,
                     s"""
                        |Runtime(${runtime.runtimeName}) was deleted from Google.
                        |""".stripMargin
                   )
                 } yield ()
               }).as(Some(runtime))
            case Some(cluster) =>
              if (cluster.getStatus.getState == com.google.cloud.dataproc.v1.ClusterStatus.State.ERROR) {
                (if (isDryRun) F.unit
                 else {
                   for {
                     isBillingEnabled <- deps.billingService.isBillingEnabled(runtime.googleProject)
                     _ <- dbReader.updateRuntimeStatus(runtime.id, "Error")
                     // If billing is enabled, then cluster may indeed be in a `Error` mode that we can still ssh to
                     // master instance to move data if necessary.
                     // If billing is disabled, we update error info to billing disabled
                     _ <-
                       if (isBillingEnabled)
                         dbReader.insertClusterError(
                           runtime.id,
                           Some(cluster.getStatus.getState.getNumber),
                           s"""
                              |Unrecoverable ERROR state for Spark Cloud Environment: ${cluster.getStatus.getDetail}
                              |
                              |Please Delete and Recreate your Cloud environment. If you have important data you’d like to retrieve
                              |from your Cloud environment prior to deleting, try to access the machine via notebook or terminal.
                              |If you can't connect to the cluster, please contact customer support and we can help you move your data.
                              |""".stripMargin
                         )
                       else
                         dbReader.insertClusterError(
                           runtime.id,
                           Some(cluster.getStatus.getState.getNumber),
                           s"""
                              |Billing is disabled for this project
                              |""".stripMargin
                         )
                   } yield ()
                 }).as(Some(runtime))
              } else F.pure(none[Runtime])
          }
        } yield res

      def checkGceRuntimeStatus(runtime: Runtime.Gce, isDryRun: Boolean): F[Option[Runtime]] =
        for {
          runtimeOpt <- deps.computeService
            .getInstance(runtime.googleProject, runtime.zone, InstanceName(runtime.runtimeName))
          _ <- if (isDryRun) F.unit
          else
            runtimeOpt match {
              case None    => dbReader.markRuntimeDeleted(runtime.id)
              case Some(_) => F.unit
            }
        } yield runtimeOpt.fold[Option[Runtime]](Some(runtime))(_ => none[Runtime])
    }
}

package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.model.TraceId

/**
 * Scans through all active nodepools and check if they still exist in Google.
 * If they're deleted from Google, we're updating them to `DELETED` in leonardo DB.
 * We're also updating any kubernetes apps running on these nodepools as `DELETED`.
 */
object DeletedNodepoolChecker {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: KubernetesClusterCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): CheckRunner[F, NodepoolToScan] =
    new CheckRunner[F, NodepoolToScan] {
      override def appName: String = zombieMonitor.appName

      override def resourceToScan: Stream[F, NodepoolToScan] = dbReader.getk8sNodepoolsToDeleteCandidate

      // the report file will container all zombied nodepool, and also error-ed nodepool
      override def configs = CheckRunnerConfigs(s"deleted-errored-nodepools", false)

      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps

      def checkResource(nodepoolToScan: NodepoolToScan,
                        isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[NodepoolToScan]] =
        for {
          nodepoolOpt <- deps.gkeService.getNodepool(nodepoolToScan.nodepoolId)
          nodepoolToReport <- nodepoolOpt match {
            case None =>
              (if (isDryRun) F.unit
               else
                 dbReader.markNodepoolAndAppStatusDeleted(nodepoolToScan.id)).as(Some(nodepoolToScan))
            case Some(nodepool) =>
              if (nodepool.getStatus == com.google.container.v1.NodePool.Status.ERROR) {
                (if (isDryRun) F.unit
                 else
                   dbReader.markNodepoolError(nodepoolToScan.id)).as(Some(nodepoolToScan))
              } else
                F.pure(none[NodepoolToScan])
          }
        } yield nodepoolToReport
    }
}

package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.GKEModels.{KubernetesClusterId, NodepoolId}
import org.broadinstitute.dsde.workbench.model.TraceId

object DeletedOrErroredNodepoolChecker {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: KubernetesClusterCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): CheckRunner[F, Nodepool] =
    new CheckRunner[F, Nodepool] {
      override def appName: String = resourceValidator.appName

      override def configs = CheckRunnerConfigs(s"deleted-nodepools", shouldAlert = true)

      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps

      override def checkResource(nodepool: Nodepool, isDryRun: Boolean)(
        implicit ev: ApplicativeAsk[F, TraceId]
      ): F[Option[Nodepool]] = checkNodepoolStatus(nodepool, isDryRun)

      override def resourceToScan: fs2.Stream[F, Nodepool] = dbReader.getDeletedAndErroredNodepools

      def checkNodepoolStatus(nodepool: Nodepool,
                              isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Nodepool]] =
        for {
          nodepoolOpt <- deps.gkeService.getNodepool(
            NodepoolId(KubernetesClusterId(nodepool.googleProject, nodepool.location, nodepool.clusterName),
                       nodepool.nodepoolName)
          )
          _ <- nodepoolOpt.traverse_ { _ =>
            if (isDryRun) {
              logger.warn(s"${nodepool.toString} still exists in Google. It needs to be deleted")
            } else {
              logger.warn(s"${nodepool.toString} still exists in Google. Going to delete") >> deps.gkeService
                .deleteNodepool(
                  NodepoolId(KubernetesClusterId(nodepool.googleProject, nodepool.location, nodepool.clusterName),
                             nodepool.nodepoolName)
                )
                .void
            }
          }
        } yield nodepoolOpt.fold(none[Nodepool])(_ => Some(nodepool))
    }
}

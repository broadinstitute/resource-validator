package com.broadinstitute.dsp
package resourceValidator

import java.util.concurrent.TimeUnit

import fs2.Stream
import cats.effect.{Concurrent, Timer}
import cats.mtl.ApplicativeAsk
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterId
import org.broadinstitute.dsde.workbench.google2.{GooglePublisher, GoogleStorageService}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

// This file will likely be moved out of resource-validator later
// See https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes?focusedCommentId=807632911#comment-807632911
object KubernetesClusterRemover {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: KubernetesClusterRemoverDeps[F]
  )(implicit F: Concurrent[F],
    timer: Timer[F],
    logger: Logger[F],
    ev: ApplicativeAsk[F, TraceId]): CheckRunner[F, KubernetesClusterToRemove] =
    new CheckRunner[F, KubernetesClusterToRemove] {
      override def appName: String = resourceValidator.appName
      override def configs = CheckRunnerConfigs(s"remove-kubernetes-clusters", true)
      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps
      override def resourceToScan: fs2.Stream[F, KubernetesClusterToRemove] = dbReader.getK8sClustersToDelete

      // TODO: pending decision in https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes
      // For now, we'll just get alerted when a cluster needs to be deleted
      override def checkResource(a: KubernetesClusterToRemove, isDryRun: Boolean)(
        implicit ev: ApplicativeAsk[F, TraceId]
      ): F[Option[KubernetesClusterToRemove]] = {
        for {
          now <- timer.clock.realTime(TimeUnit.MILLISECONDS)
            traceId = Some(TraceId(s"resourceValidator-$now"))
          _ <- if (!isDryRun) {
            val msg = DeleteKubernetesClusterMessage(
              a.id,
              traceId
            )
            // TODO: Add publishOne in wb-libs and use it here
            val r = Stream.emit(msg).covary[F] through deps.publisher.publish[DeleteKubernetesClusterMessage]
            r.compile.drain
          }else F.unit
        } yield Some(a)
      }
    }

}

final case class KubernetesClusterToRemove(id: Long, googleProject: GoogleProject)
final case class DeleteKubernetesClusterMessage(clusterId: Long,
                                                traceId: Option[TraceId]) {
  val messageType: String = "deleteKubernetesCluster"
}

final case class KubernetesClusterRemoverDeps[F[_]](publisher: GooglePublisher[F],
                                                    checkRunnerDeps: CheckRunnerDeps[F])

package com.broadinstitute.dsp.janitor

import cats.effect.{Concurrent, Timer}
import cats.mtl.Ask
import cats.syntax.all._
import com.broadinstitute.dsp._
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.google2.JsonCodec.{googleProjectEncoder, traceIdEncoder}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.typelevel.log4cats.Logger

import java.util.concurrent.TimeUnit

// This file will likely be moved out of resource-validator later
// See https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes?focusedCommentId=807632911#comment-807632911
object KubernetesClusterRemover {
  implicit val deleteKubernetesClusterMessageEncoder: Encoder[DeleteKubernetesClusterMessage] =
    Encoder.forProduct4("messageType", "clusterId", "project", "traceId")(x =>
      (x.messageType, x.clusterId, x.project, x.traceId)
    )

  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: LeoPublisherDeps[F]
  )(implicit
    F: Concurrent[F],
    timer: Timer[F],
    logger: Logger[F],
    ev: Ask[F, TraceId]
  ): CheckRunner[F, KubernetesClusterToRemove] =
    new CheckRunner[F, KubernetesClusterToRemove] {
      override def appName: String = janitor.appName
      override def configs = CheckRunnerConfigs(s"remove-kubernetes-clusters", shouldAlert = false)
      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps
      override def resourceToScan: fs2.Stream[F, KubernetesClusterToRemove] = dbReader.getKubernetesClustersToDelete

      // https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes
      override def checkResource(c: KubernetesClusterToRemove, isDryRun: Boolean)(implicit
        ev: Ask[F, TraceId]
      ): F[Option[KubernetesClusterToRemove]] =
        for {
          now <- timer.clock.realTime(TimeUnit.MILLISECONDS)
          _ <-
            if (!isDryRun) {
              val msg = DeleteKubernetesClusterMessage(c.id, c.googleProject, TraceId(s"kubernetesClusterRemover-$now"))
              deps.publisher.publishOne(msg)
            } else F.unit
        } yield Some(c)
    }
}

final case class DeleteKubernetesClusterMessage(clusterId: Long, project: GoogleProject, traceId: TraceId) {
  val messageType: String = "deleteKubernetesCluster"
}

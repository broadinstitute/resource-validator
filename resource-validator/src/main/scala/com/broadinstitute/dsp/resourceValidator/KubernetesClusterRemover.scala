package com.broadinstitute.dsp
package resourceValidator

import java.util.concurrent.TimeUnit

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.Ask
import io.chrisdavenport.log4cats.Logger
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.google2.JsonCodec.traceIdEncoder
import org.broadinstitute.dsde.workbench.google2.GooglePublisher
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GoogleProject

// This file will likely be moved out of resource-validator later
// See https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes?focusedCommentId=807632911#comment-807632911
object KubernetesClusterRemover {
  import LeoPubsubCodec._

  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: KubernetesClusterRemoverDeps[F]
  )(implicit F: Concurrent[F],
    timer: Timer[F],
    logger: Logger[F],
    ev: Ask[F, TraceId]): CheckRunner[F, KubernetesClusterToRemove] =
    new CheckRunner[F, KubernetesClusterToRemove] {
      override def appName: String = resourceValidator.appName
      override def configs = CheckRunnerConfigs(s"remove-kubernetes-clusters", shouldAlert = true)
      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps
      override def resourceToScan: fs2.Stream[F, KubernetesClusterToRemove] = dbReader.getKubernetesClustersToDelete

      // TODO: This check is to be moved to a new project (a.k.a. 'janitor)
      // https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes
      override def checkResource(c: KubernetesClusterToRemove, isDryRun: Boolean)(
        implicit ev: Ask[F, TraceId]
      ): F[Option[KubernetesClusterToRemove]] =
        for {
          now <- timer.clock.realTime(TimeUnit.MILLISECONDS)
          _ <- if (!isDryRun) {
            val msg = DeleteKubernetesClusterMessage(c.id, c.googleProject, TraceId(s"kubernetesClusterRemover-$now"))
            deps.publisher.publishOne(msg)
          } else F.unit
        } yield Some(c)
    }
}

// TODO: 'project' below is unnecessary but removing it requires an accompanying change in back Leo so leaving for now
final case class DeleteKubernetesClusterMessage(clusterId: Long, project: GoogleProject, traceId: TraceId) {
  val messageType: String = "deleteKubernetesCluster"
}

final case class KubernetesClusterRemoverDeps[F[_]](publisher: GooglePublisher[F], checkRunnerDeps: CheckRunnerDeps[F])

object JsonCodec {
  implicit val googleProjectEncoder: Encoder[GoogleProject] = Encoder.encodeString.contramap(_.value)
}

object LeoPubsubCodec {
  import JsonCodec._

  implicit val deleteKubernetesClusterMessageEncoder: Encoder[DeleteKubernetesClusterMessage] =
    Encoder.forProduct4("messageType", "clusterId", "project", "traceId")(x =>
      (x.messageType, x.clusterId, x.project, x.traceId)
    )
}

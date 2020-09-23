package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.mtl.ApplicativeAsk
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterId
import org.broadinstitute.dsde.workbench.google2.GoogleStorageService
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName

// This file will likely be moved out of resource-validator later
// See https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes?focusedCommentId=807632911#comment-807632911
object KubernetesClusterRemover {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: CheckRunnerDeps[F]
  )(implicit F: Concurrent[F],
    timer: Timer[F],
    logger: Logger[F],
    ev: ApplicativeAsk[F, TraceId]): CheckRunner[F, KubernetesClusterId] =
    new CheckRunner[F, KubernetesClusterId] {
      override def appName: String = resourceValidator.appName
      override def configs = CheckRunnerConfigs(s"remove-kubernetes-clusters", true)
      override def dependencies: CheckRunnerDeps[F] = deps
      override def resourceToScan: fs2.Stream[F, KubernetesClusterId] = dbReader.getK8sClustersToDelete

      // TODO: pending decision in https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes
      // For now, we'll just get alerted when a cluster needs to be deleted
      override def checkResource(a: KubernetesClusterId, isDryRun: Boolean)(
        implicit ev: ApplicativeAsk[F, TraceId]
      ): F[Option[KubernetesClusterId]] = F.pure(Some(a))
    }

}

final case class KubernetesClusterRemover[F[_]](reportDestinationBucket: GcsBucketName,
                                                storageService: GoogleStorageService[F])

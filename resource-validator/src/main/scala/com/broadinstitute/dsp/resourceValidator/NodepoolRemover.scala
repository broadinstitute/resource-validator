package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.syntax.all._
import cats.mtl.Ask
import org.typelevel.log4cats.Logger
import org.broadinstitute.dsde.workbench.model.TraceId
import JsonCodec._

// This file will likely be moved out of resource-validator later
// See https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes?focusedCommentId=807632911#comment-807632911
object NodepoolRemover {

  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: LeoPublisherDeps[F]
  )(implicit F: Concurrent[F], timer: Timer[F], logger: Logger[F], ev: Ask[F, TraceId]): CheckRunner[F, Nodepool] =
    new CheckRunner[F, Nodepool] {
      override def appName: String = resourceValidator.appName
      override def configs = CheckRunnerConfigs(s"remove-kubernetes-nodepools", shouldAlert = false)
      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps
      override def resourceToScan: fs2.Stream[F, Nodepool] = dbReader.getNodepoolsToDelete

      // TODO: This check is to be moved to a new project (a.k.a. 'janitor)
      // https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes
      override def checkResource(n: Nodepool, isDryRun: Boolean)(
        implicit ev: Ask[F, TraceId]
      ): F[Option[Nodepool]] =
        for {
          ctx <- ev.ask
          _ <- if (!isDryRun) {
            val msg = DeleteNodepoolMeesage(n.nodepoolId, n.googleProject, Some(ctx))
            deps.publisher.publishOne(msg)
          } else F.unit
        } yield Some(n)
    }
}

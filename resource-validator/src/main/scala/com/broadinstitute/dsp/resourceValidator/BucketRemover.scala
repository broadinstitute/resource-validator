package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.GoogleStorageService
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName

// This file will likely be moved out of resource-validator later
// See https://broadworkbench.atlassian.net/wiki/spaces/IA/pages/807436289/2020-09-17+Leonardo+Async+Processes?focusedCommentId=807632911#comment-807632911
object BucketRemover {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: CheckRunnerDeps[F]
  )(implicit F: Concurrent[F],
    timer: Timer[F],
    logger: Logger[F],
    ev: ApplicativeAsk[F, TraceId]): CheckRunner[F, BucketToRemove] =
    new CheckRunner[F, BucketToRemove] {
      override def configs = CheckRunnerConfigs("remove-staging-buckets", false)
      override def dependencies: CheckRunnerDeps[F] = deps
      override def aToScan: fs2.Stream[F, BucketToRemove] = dbReader.getBucketsToDelete

      // We're ignoring isDryRun flag here since we do want to delete these staging buckets
      override def checkA(a: BucketToRemove, isDryRun: Boolean)(
        implicit ev: ApplicativeAsk[F, TraceId]
      ): F[Option[BucketToRemove]] =
        a.bucket
          .traverse(b => deps.storageService.deleteBucket(a.googleProject, b, true).compile.drain.as(a))
    }

}

final case class BucketRemoverDeps[F[_]](reportDestinationBucket: GcsBucketName,
                                         storageService: GoogleStorageService[F])

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
      override def appName: String = resourceValidator.appName
      override def configs = CheckRunnerConfigs("remove-staging-buckets", false)
      override def dependencies: CheckRunnerDeps[F] = deps
      override def resourceToScan: fs2.Stream[F, BucketToRemove] = dbReader.getBucketsToDelete

      // We're ignoring isDryRun flag here since we do want to delete these staging buckets
      // We can improve this by checking if the bucket exists first, but it doesn't hurt to blindly issue deleting bucket
      // either except it'll be a bit confusing in report, it'll look like this many staging buckets are deleted for a particular run.
      override def checkResource(a: BucketToRemove, isDryRun: Boolean)(
        implicit ev: ApplicativeAsk[F, TraceId]
      ): F[Option[BucketToRemove]] =
        a.bucket
          .flatTraverse { b =>
            deps.storageService.deleteBucket(a.googleProject, b, true).compile.last.map { resultOpt =>
              // deleteBucket will return `true` if the bucket is deleted; else return `false`
              // We only need to report buckets that're actually being deleted
              resultOpt match {
                case Some(true)  => Some(a)
                case Some(false) => None
                case None        => None
              }
            }
          }
    }

}

final case class BucketRemoverDeps[F[_]](reportDestinationBucket: GcsBucketName,
                                         storageService: GoogleStorageService[F])

package com.broadinstitute.dsp
package resourceValidator

import java.nio.charset.Charset
import java.time.Instant
import java.util.concurrent.TimeUnit
import cats.implicits._
import cats.effect.{Concurrent, Timer}
import cats.mtl.ApplicativeAsk
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{
  GcsBlobName,
  GoogleComputeService,
  GoogleDataprocService,
  GoogleStorageService
}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.model.google.GcsBucketName

trait BucketRemover[F[_]] {
  def removeBuckets(): F[Unit]
}

object BucketRemover {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: BucketRemoverDeps[F]
  )(implicit F: Concurrent[F], timer: Timer[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): BucketRemover[F] =
    new BucketRemover[F] {
      override def removeBuckets: F[Unit] =
        for {
          now <- timer.clock.realTime(TimeUnit.MILLISECONDS)
          blobName = GcsBlobName(s"removed-staging-buckets/action-${Instant.ofEpochMilli(now)}")
          _ <- (runtimesToScan
            .parEvalMapUnordered(50)(rt => checkRuntimeStatus(rt, isDryRun).handleErrorWith(_ => F.pure(None)))
            .unNone
            .map(_.toString)
            .intersperse("\n")
            .map(_.getBytes(Charset.forName("UTF-8")))
            .flatMap(arrayOfBytes => Stream.emits(arrayOfBytes))
            .through(
              dependencies.storageService.streamUploadBlob(
                dependencies.reportDestinationBucket,
                blobName
              )
            ))
            .compile
            .drain
          blob <- dependencies.storageService.getBlob(dependencies.reportDestinationBucket, blobName).compile.last
          _ <- blob.traverse { b =>
            if (b.getSize == 0L)
              logger.warn(s"${checkType} | No anomaly detected") >> dependencies.storageService
                .removeObject(dependencies.reportDestinationBucket, blobName)
                .compile
                .drain
            else
              logger.error(
                s"${checkType} | Anomaly detected. Check out gs://${dependencies.reportDestinationBucket.value}/${blobName.value} for more details"
              )
          }
        } yield ()
    }

}

final case class BucketRemoverDeps[F[_]](reportDestinationBucket: GcsBucketName,
                                         storageService: GoogleStorageService[F])

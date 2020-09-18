package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.GoogleDiskService
import org.broadinstitute.dsde.workbench.model.TraceId

// Implements CheckRunner[F[_], A]
object DeletedDiskChecker {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: DeletedDiskCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): CheckRunner[F, Disk] =
    new CheckRunner[F, Disk] {
      override def configs = CheckRunnerConfigs("deleted-disks", true)
      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps
      override def aToScan: fs2.Stream[F, Disk] = dbReader.getDeletedDisks

      override def checkA(disk: Disk, isDryRun: Boolean)(
        implicit ev: ApplicativeAsk[F, TraceId]
      ): F[Option[Disk]] =
        for {
          diskOpt <- deps.googleDiskService.getDisk(disk.googleProject, zoneName, disk.diskName)
          r <- if (!isDryRun) {
            diskOpt.traverse(_ =>
              deps.googleDiskService.deleteDisk(disk.googleProject, zoneName, disk.diskName).as(disk)
            )
          } else F.pure(Some(disk))
        } yield r
    }
}

final case class DeletedDiskCheckerDeps[F[_]](checkRunnerDeps: CheckRunnerDeps[F],
                                              googleDiskService: GoogleDiskService[F])

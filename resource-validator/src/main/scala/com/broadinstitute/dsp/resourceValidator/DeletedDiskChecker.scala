package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.model.TraceId

// Implements CheckRunner[F[_], A]
object DeletedDiskChecker {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: DiskCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): CheckRunner[F, Disk] =
    new CheckRunner[F, Disk] {
      override def appName: String = resourceValidator.appName
      override def configs = CheckRunnerConfigs(s"deleted-disks", true)
      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps
      override def resourceToScan: fs2.Stream[F, Disk] = dbReader.getDeletedDisks

      override def checkResource(disk: Disk, isDryRun: Boolean)(
        implicit ev: ApplicativeAsk[F, TraceId]
      ): F[Option[Disk]] =
        for {
          diskOpt <- deps.googleDiskService.getDisk(disk.googleProject, zoneName, disk.diskName)
          _ <- if (!isDryRun) {
            diskOpt.traverse(_ => deps.googleDiskService.deleteDisk(disk.googleProject, zoneName, disk.diskName))
          } else F.pure(None)
        } yield diskOpt.map(_ => disk)
    }
}

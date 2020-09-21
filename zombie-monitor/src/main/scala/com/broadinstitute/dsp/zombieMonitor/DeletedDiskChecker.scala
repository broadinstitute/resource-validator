package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.{DataprocClusterName, InstanceName}
import org.broadinstitute.dsde.workbench.model.TraceId

/**
 * Similar to `DeletedDiskChecker` in `resource-validator`, but this process all disks and check if they still exists in google.
 * If not, we update leonardo DB to reflect that they're deleted
 */
object DeletedDiskChecker {
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: DiskCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: ApplicativeAsk[F, TraceId]): CheckRunner[F, Runtime] =
    new CheckRunner[F, Disk] {
      override def resourceToScan: Stream[F, Disk] = dbReader.

      override def configs = CheckRunnerConfigs("zombie-monitor/deleted-runtime", true)

      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps

      def checkResource(disk: Disk, isDryRun: Boolean)(implicit ev: ApplicativeAsk[F, TraceId]): F[Option[Disk]] =
        for {
          diskOpt <- deps.googleDiskService.getDisk(disk.googleProject, zoneName, disk.diskName)
          _ <- if (!isDryRun) {
            diskOpt.traverse(_ => deps.googleDiskService.deleteDisk(disk.googleProject, zoneName, disk.diskName))
          } else F.pure(None)
        } yield diskOpt.map(_ => disk)
    }
}

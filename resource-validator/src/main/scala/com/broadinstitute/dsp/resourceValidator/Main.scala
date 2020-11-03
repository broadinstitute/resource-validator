package com.broadinstitute.dsp
package resourceValidator

import cats.effect.IO
import cats.implicits._
import com.monovore.decline.{CommandApp, _}
import scala.concurrent.ExecutionContext.global

object Main
    extends CommandApp(
      name = "resource-validator",
      header = "Update Google resources to reflect Leonardo database",
      version = "0.0.1",
      main = {
        implicit val cs = IO.contextShift(global)
        implicit val timer = IO.timer(global)

        val enableDryRun = Opts.flag("dryRun", "Default to true").map(_ => true).withDefault(true)
        val shouldCheckAll = Opts.flag("all", "run all checks").orFalse
        val shouldCheckDeletedRuntimes = Opts.flag("checkDeletedRuntimes", "check all deleted runtimes").orFalse
        val shouldCheckErroredRuntimes = Opts.flag("checkErroredRuntimes", "check all errored runtimes").orFalse
        val shouldCheckStoppedGceRuntimes =
          Opts.flag("checkStoppedGceRuntimes", "check all stopped GCE runtimes").orFalse
        val shouldCheckStoppedDataprocRuntimes =
          Opts.flag("checkStoppedDataprocRuntimes", "check all stopped Dataproc runtimes").orFalse
        val shouldCheckDeletedDisks = Opts.flag("checkDeletedDisks", "check all deleted disks").orFalse
        val shouldCheckInitBuckets =
          Opts.flag("checkInitBuckets", "checks that init buckets for deleted runtimes are deleted").orFalse

        (enableDryRun,
         shouldCheckAll,
         shouldCheckDeletedRuntimes,
         shouldCheckErroredRuntimes,
         shouldCheckStoppedGceRuntimes,
         shouldCheckStoppedDataprocRuntimes,
         shouldCheckDeletedDisks,
         shouldCheckInitBuckets).mapN {
          (dryRun,
           checkAll,
           shouldCheckDeletedRuntimes,
           shouldCheckErroredRuntimes,
           shouldCheckStoppedGceRuntimes,
           shouldCheckStoppedDataprocRuntimes,
           shouldCheckDeletedDisks,
           shouldCheckInitBuckets) =>
            ResourceValidator
              .run[IO](
                dryRun,
                checkAll,
                shouldCheckDeletedRuntimes,
                shouldCheckErroredRuntimes,
                shouldCheckStoppedGceRuntimes,
                shouldCheckStoppedDataprocRuntimes,
                shouldCheckDeletedDisks,
                shouldCheckInitBuckets
              )
              .compile
              .drain
              .unsafeRunSync()
        }
      }
    )

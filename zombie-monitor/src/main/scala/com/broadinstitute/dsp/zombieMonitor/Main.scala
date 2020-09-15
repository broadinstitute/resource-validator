package com.broadinstitute.dsp.zombieMonitor

import cats.effect.IO
import cats.implicits._
import com.monovore.decline.{CommandApp, _}
import scala.concurrent.ExecutionContext.global

object Main
    extends CommandApp(
      name = "zombie-monitor",
      header = "Update Leonardo DB to Reflect Google Resource Status",
      version = "0.0.1",
      main = {
        implicit val cs = IO.contextShift(global)
        implicit val timer = IO.timer(global)

        val enableDryRun = Opts.flag("dryRun", "Default to true").map(_ => true).withDefault(true)
        val ifRunAll = Opts.flag("all", "run all checks").orFalse
        val ifRunCheckDeletedRuntimes = Opts.flag("checkDeletedRuntimes", "check all deleted runtimes").orFalse

        (enableDryRun, ifRunAll, ifRunCheckDeletedRuntimes).mapN { (dryRun, runAll, runCheckDeletedRuntimes) =>
          ZombieMonitor
            .run[IO](dryRun, runAll, runCheckDeletedRuntimes)
            .compile
            .drain
            .unsafeRunSync()
        }
      }
    )

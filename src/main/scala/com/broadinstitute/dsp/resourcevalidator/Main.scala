package com.broadinstitute.dsp.resourcevalidator

import cats.effect.IO
import cats.implicits._
import com.monovore.decline.{CommandApp, _}
import scala.concurrent.ExecutionContext.global

object Main
    extends CommandApp(
      name = "resource-validator",
      header = "We care about users",
      version = "0.0.1",
      main = {
        implicit val cs = IO.contextShift(global)
        implicit val timer = IO.timer(global)

        val enableDryRun = Opts.flag("dryRun", "Default to true").map(_ => true).withDefault(true)
        val ifRunAll = Opts.flag("all", "run all checks").orFalse
        val ifRunCheckDeletedRuntimes = Opts.flag("checkDeletedRuntimes", "check all deleted runtimes").orFalse

        (enableDryRun, ifRunAll, ifRunCheckDeletedRuntimes).mapN { (dryRun, runAll, runCheckDeletedRuntimes) =>
          Resourcevalidator.run[IO](dryRun, runAll, runCheckDeletedRuntimes).compile.drain.unsafeRunSync()
        }
      }
    )

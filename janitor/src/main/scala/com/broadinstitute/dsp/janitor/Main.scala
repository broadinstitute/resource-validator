package com.broadinstitute.dsp
package janitor

import cats.effect.IO
import cats.syntax.all._
import com.monovore.decline.{CommandApp, _}
import scala.concurrent.ExecutionContext.global

object Main
    extends CommandApp(
      name = "janitor",
      header = "Clean up prod resources deemed not utilized",
      version = "0.0.1",
      main = {
        implicit val cs = IO.contextShift(global)
        implicit val timer = IO.timer(global)

        val enableDryRun = Opts.flag("dryRun", "Default to true").orFalse.withDefault(true)
        val shouldRunAll = Opts.flag("all", "run all checks").orFalse
        val shouldDeletePubsubTopics =
          Opts.flag("deletePubsubTopics", "delete all fiab pubsub topics").orFalse

        (enableDryRun, shouldRunAll, shouldDeletePubsubTopics).mapN { (dryRun, runAll, deletePubsubTopics) =>
          Janitor
            .run[IO](dryRun, runAll, deletePubsubTopics)
            .compile
            .drain
            .unsafeRunSync()
        }
      }
    )

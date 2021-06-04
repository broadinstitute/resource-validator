package com.broadinstitute.dsp
package janitor

import cats.effect.IO
import cats.mtl.Ask
import com.broadinstitute.dsp.Generators._
import fs2.Stream
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.google2.GooglePublisher
import org.broadinstitute.dsde.workbench.google2.mock.{FakeGooglePublisher, FakeGoogleStorageInterpreter}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.openTelemetry.FakeOpenTelemetryMetricsInterpreter
import org.scalatest.flatspec.AnyFlatSpec

final class KubernetesClusterRemoverSpec extends AnyFlatSpec with CronJobsTestSuite {
  it should "send DeleteKubernetesClusterMessage when clusters are detected to be auto-deleted" in {
    forAll { (clusterToRemove: KubernetesClusterToRemove, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getKubernetesClustersToDelete: Stream[IO, KubernetesClusterToRemove] =
          Stream.emit(clusterToRemove)
      }

      var count = 0

      val publisher = new FakeGooglePublisher {
        override def publishOne[MessageType](message: MessageType)(implicit
          evidence$2: Encoder[MessageType],
          ev: Ask[IO, TraceId]
        ): IO[Unit] =
          if (dryRun)
            IO.raiseError(fail("Shouldn't publish message in dryRun mode"))
          else {
            count = count + 1
            super.publishOne(message)(evidence$2, ev)
          }
      }

      val deps = initDeps(publisher)
      val checker = KubernetesClusterRemover.impl(dbReader, deps)
      val res = checker.checkResource(clusterToRemove, dryRun)

      res.unsafeRunSync() shouldBe Some(clusterToRemove)
      if (dryRun) count shouldBe 0
      else count shouldBe 1
    }
  }

  private def initDeps(publisher: GooglePublisher[IO]): LeoPublisherDeps[IO] = {
    val checkRunnerDeps =
      CheckRunnerDeps(ConfigSpec.config.reportDestinationBucket,
                      FakeGoogleStorageInterpreter,
                      FakeOpenTelemetryMetricsInterpreter
      )
    new LeoPublisherDeps[IO](publisher, checkRunnerDeps)
  }
}

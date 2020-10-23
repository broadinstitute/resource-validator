package com.broadinstitute.dsp
package resourceValidator

import cats.effect.IO
import com.broadinstitute.dsp.Generators._
import fs2.{Pipe, Stream}
import io.circe.Encoder
import org.broadinstitute.dsde.workbench.google2.GooglePublisher
import org.broadinstitute.dsde.workbench.google2.mock.FakeGoogleStorageInterpreter
import org.scalatest.flatspec.AnyFlatSpec

class KubernetesClusterRemoverSpec extends AnyFlatSpec with CronJobsTestSuite {
  it should "send DeleteKubernetesClusterMessage when clusters are detected to be auto-deleted" in {
    forAll { (clusterToRemove: KubernetesClusterToRemove, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getKubernetesClustersToDelete: Stream[IO, KubernetesClusterToRemove] =
          Stream.emit(clusterToRemove)
      }

      var count = 0

      val publisher = new FakeGooglePublisher {
        override def publish[MessageType](implicit ev: Encoder[MessageType]): Pipe[IO, MessageType, Unit] =
          if (dryRun)
            in => in.evalMap(_ => IO.raiseError(fail("Shouldn't publish message in dryRun mode")))
          else {
            count = count + 1
            super.publish
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

  private def initDeps(publisher: GooglePublisher[IO]): KubernetesClusterRemoverDeps[IO] = {
    val checkRunnerDeps = CheckRunnerDeps(ConfigSpec.config.reportDestinationBucket, FakeGoogleStorageInterpreter)
    new KubernetesClusterRemoverDeps[IO](publisher, checkRunnerDeps)
  }
}

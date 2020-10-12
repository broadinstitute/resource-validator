package com.broadinstitute.dsp
package zombieMonitor

import cats.effect.IO
import com.broadinstitute.dsp.Generators._
import fs2.Stream
import org.broadinstitute.dsde.workbench.google2.GKEModels.NodepoolId
import org.broadinstitute.dsde.workbench.google2.GKEService
import org.broadinstitute.dsde.workbench.google2.mock.{FakeGoogleStorageInterpreter, MockGKEService}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.scalatest.flatspec.AnyFlatSpec

class DeletedNodepoolCheckerSpec extends AnyFlatSpec with CronJobsTestSuite {
  it should "report nodepool if it doesn't exist in google but still active in leonardo DB" in {
    forAll { (nodepoolToScan: NodepoolToScan, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getk8sNodepoolsToDeleteCandidate: Stream[IO, NodepoolToScan] =
          Stream.emit(nodepoolToScan)
        override def markNodepoolAndAppStatusDeleted(id: Long): IO[Unit] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called in dryRun mode")) else IO.unit
      }
      val gkeService = new MockGKEService {
        override def getNodepool(nodepoolId: NodepoolId)(
          implicit ev: cats.mtl.ApplicativeAsk[IO, TraceId]
        ): IO[Option[com.google.container.v1.NodePool]] = IO.pure(None)
      }
      val deps = initDeps(gkeService)
      val checker = DeletedNodepoolChecker.impl(dbReader, deps)
      val res = checker.checkResource(nodepoolToScan, dryRun)
      res.unsafeRunSync() shouldBe Some(nodepoolToScan)
    }
  }

  it should "don't report nodepool if it still exist in google and active in leonardo DB" in {
    forAll { (nodepoolToScan: NodepoolToScan, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getk8sNodepoolsToDeleteCandidate: Stream[IO, NodepoolToScan] =
          Stream.emit(nodepoolToScan)
      }
      val gkeService = new MockGKEService {
        override def getNodepool(nodepoolId: NodepoolId)(
          implicit ev: cats.mtl.ApplicativeAsk[IO, TraceId]
        ): IO[Option[com.google.container.v1.NodePool]] =
          IO.pure(Some(com.google.container.v1.NodePool.newBuilder().build()))
      }
      val deps = initDeps(gkeService)
      val checker = DeletedNodepoolChecker.impl(dbReader, deps)
      val res = checker.checkResource(nodepoolToScan, dryRun)
      res.unsafeRunSync() shouldBe None
    }
  }

  it should "report nodepool if it still exist in google in ERROR and active in leonardo DB" in {
    forAll { (nodepoolToScan: NodepoolToScan, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getk8sNodepoolsToDeleteCandidate: Stream[IO, NodepoolToScan] =
          Stream.emit(nodepoolToScan)

        override def markNodepoolError(id: Long): IO[Unit] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called in dryRun mode")) else IO.unit
      }
      val gkeService = new MockGKEService {
        override def getNodepool(nodepoolId: NodepoolId)(
          implicit ev: cats.mtl.ApplicativeAsk[IO, TraceId]
        ): IO[Option[com.google.container.v1.NodePool]] =
          IO.pure(
            Some(
              com.google.container.v1.NodePool
                .newBuilder()
                .setStatus(com.google.container.v1.NodePool.Status.ERROR)
                .build()
            )
          )
      }
      val deps = initDeps(gkeService)
      val checker = DeletedNodepoolChecker.impl(dbReader, deps)
      val res = checker.checkResource(nodepoolToScan, dryRun)
      res.unsafeRunSync() shouldBe Some(nodepoolToScan)
    }
  }

  def initDeps(gkeSerivce: GKEService[IO]): KubernetesClusterCheckerDeps[IO] = {
    val checkRunnerDeps = CheckRunnerDeps(config.reportDestinationBucket, FakeGoogleStorageInterpreter)
    new KubernetesClusterCheckerDeps[IO](checkRunnerDeps, gkeSerivce)
  }
}

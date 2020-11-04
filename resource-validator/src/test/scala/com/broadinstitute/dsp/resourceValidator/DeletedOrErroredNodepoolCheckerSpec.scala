package com.broadinstitute.dsp
package resourceValidator

import cats.effect.IO
import cats.mtl.ApplicativeAsk
import com.broadinstitute.dsp.Generators._
import com.broadinstitute.dsp.resourceValidator.InitDependenciesHelper.initKubernetesClusterCheckerDeps
import com.google.container.v1.{NodePool, Operation}
import fs2.Stream
import org.broadinstitute.dsde.workbench.google2.GKEModels.NodepoolId
import org.broadinstitute.dsde.workbench.google2.mock.MockGKEService
import org.broadinstitute.dsde.workbench.model.TraceId
import org.scalatest.flatspec.AnyFlatSpec

class DeletedOrErroredNodepoolCheckerSpec extends AnyFlatSpec with CronJobsTestSuite {
  it should "return None if nodepool no longer exists in Google" in {
    val gkeService = new MockGKEService {
      override def getNodepool(nodepoolId: NodepoolId)(
        implicit ev: ApplicativeAsk[IO, TraceId]
      ): IO[Option[NodePool]] = IO.pure(None)
    }

    val deps = initKubernetesClusterCheckerDeps(gkeService = gkeService)

    forAll { (nodepool: Nodepool, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getDeletedAndErroredNodepools: fs2.Stream[IO, Nodepool] = Stream.emit(nodepool)
      }
      val deletedOrErroredNodepoolChecker =
        DeletedOrErroredNodepoolChecker.impl(dbReader, deps)
      val res = deletedOrErroredNodepoolChecker.checkResource(nodepool, dryRun)
      res.unsafeRunSync() shouldBe None
    }
  }

  it should "return nodepool if nodepool still exists in Google" in {
    forAll { (nodepool: Nodepool, dryRun: Boolean) =>
      val dbReader = new FakeDbReader {
        override def getDeletedAndErroredNodepools: fs2.Stream[IO, Nodepool] = Stream.emit(nodepool)
      }
      val gkeService = new MockGKEService {
        override def getNodepool(nodepoolId: NodepoolId)(
          implicit ev: ApplicativeAsk[IO, TraceId]
        ): IO[Option[NodePool]] = {
          val nodepool = NodePool.newBuilder().build()
          IO.pure(Some(nodepool))
        }

        override def deleteNodepool(nodepoolId: NodepoolId)(
          implicit ev: ApplicativeAsk[IO, TraceId]
        ): IO[Option[Operation]] =
          if (dryRun) IO.raiseError(fail("this shouldn't be called")) else IO.pure(Some(Operation.newBuilder().build()))
      }

      val deps = initKubernetesClusterCheckerDeps(gkeService = gkeService)

      val deletedOrErroredNodepoolChecker = DeletedOrErroredNodepoolChecker.impl(dbReader, deps)
      val res = deletedOrErroredNodepoolChecker.checkResource(nodepool, dryRun)
      res.unsafeRunSync() shouldBe Some(nodepool)
    }
  }
}

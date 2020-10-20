package com.broadinstitute.dsp
package resourceValidator

import java.time.Instant

import com.broadinstitute.dsp.DBTestHelper._
import com.broadinstitute.dsp.Generators._
import doobie.scalatest.IOChecker
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterId
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName.NamespaceName
import org.scalatest.DoNotDiscover
import org.scalatest.flatspec.AnyFlatSpec

/**
 * Not running these tests in CI yet since we'll need to set up mysql container and Leonardo tables in CI. Punt for now
 * For running these tests locally, you can
 *   * Start leonardo mysql container locally
 *   * Comment out https://github.com/DataBiosphere/leonardo/blob/develop/http/src/test/scala/org/broadinstitute/dsde/workbench/leonardo/db/TestComponent.scala#L82
 *   * Run a database unit test in leonardo
 *   * Run this spec
 */
@DoNotDiscover
class DbReaderSpec extends AnyFlatSpec with CronJobsTestSuite with IOChecker {
  val transactor = yoloTransactor

  it should "build deletedDisksQuery properly" in {
    check(DbReader.deletedDisksQuery)
  }

  it should "build initBucketsToDeleteQuery properly" in {
    check(DbReader.initBucketsToDeleteQuery)
  }

  it should "build kubernetesClustersToDeleteQuery properly" in {
    check(DbReader.kubernetesClustersToDeleteQuery)
  }

  // TODO: Rename this spec as 'DbQueryBuilderSpec' and split out checker-functionality-specific tests below to their own Spec's
  it should "detect Kubernetes clusters to be removed" in {
    val now = Instant.now()
    val gracePeriod = 3600 // in seconds

    val dateAccessedDefault = Instant.MIN // -1000000000-01-01T00:00:00Z
    val dateAccessedBeyondGracePeriod = now.minusSeconds(gracePeriod + 100)
    val dateAccessedWithinGracePeriod = now.minusSeconds(gracePeriod - 50)

    val destroyedDateDefault = Instant.ofEpochSecond(1) // 1970-01-01T00:00:01Z
    val destroyedDateBeyondGracePeriod = now.minusSeconds(gracePeriod + 200)
    val destroyedDateWithinGracePeriod = now.minusSeconds(gracePeriod - 150)

    forAll { (cluster: KubernetesClusterId, disk: Disk) =>
      val res = transactorResource.use { implicit xa =>
        val dbReader = DbReader.impl(xa)

        for {
          diskId <- insertDisk(disk)
          cluster1Id <- insertK8sCluster(cluster, "RUNNING")
          nodepool1Id <- insertNodepool(cluster1Id, "np1", false)
          namespaceId <- insertNamespace(cluster1Id, NamespaceName("ns1"))
          _ <- insertApp(nodepool1Id,
                         namespaceId,
                         "app1",
                         diskId,
                         "DELETED",
                         destroyedDateBeyondGracePeriod,
                         dateAccessedWithinGracePeriod)
          clustersToRemove <- dbReader.getKubernetesClustersToDelete.compile.toList
        } yield clustersToRemove.map(_.id) should contain theSameElementsAs List(cluster1Id)
      }
      res.unsafeRunSync()
    }
  }
}

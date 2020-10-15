package com.broadinstitute.dsp
package zombieMonitor

import com.broadinstitute.dsp.DBTestHelper._
import com.broadinstitute.dsp.Generators._
import doobie.scalatest.IOChecker
import org.broadinstitute.dsde.workbench.google2.DiskName
import org.broadinstitute.dsde.workbench.google2.GKEModels.{KubernetesClusterId, KubernetesClusterName}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.scalatest.DoNotDiscover
import org.scalatest.flatspec.AnyFlatSpec
import doobie.implicits._
import org.broadinstitute.dsde.workbench.google2.KubernetesSerializableName.NamespaceName

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

  it should "build activeDisksQuery properly" in {
    check(DbReader.activeDisksQuery)
  }

  it should "build activeK8sClustersQuery properly" in {
    check(DbReader.activeK8sClustersQuery)
  }

  it should "build activeNodepoolsQuery properly" in {
    check(DbReader.activeNodepoolsQuery)
  }

  // This test will fail with `Parameter metadata not available for the given statement`
  // This works fine for real, but doesn't work `check` due to limited support for metadata from mysql
  it should "builds updateDiskStatusQuery properly" ignore {
    check(DbReader.updateDiskStatusQuery(82))
  }

  it should "read a disk properly" in {
    forAll { (disk: Disk) =>
      val res = transactorResource.use { implicit xa =>
        val dbReader = DbReader.impl(xa)

        val creatingDisk = disk.copy(diskName = DiskName("disk2"))
        val readyDisk = disk.copy(diskName = DiskName("disk3"))

        for {
          _ <- insertDisk(disk, "Deleted")
          _ <- insertDisk(creatingDisk, "Creating")
          _ <- insertDisk(readyDisk)
          d <- dbReader.getDisksToDeleteCandidate.compile.toList
        } yield {
          d.map(_.copy(id = 0L)) should contain theSameElementsAs List(creatingDisk, readyDisk).map(_.copy(id = 0L))
        }
      }
      res.unsafeRunSync()
    }
  }

  it should "read a K8sClusterToScan properly" in {
    forAll { (cluster: KubernetesClusterId) =>
      val res = transactorResource.use { implicit xa =>
        val dbReader = DbReader.impl(xa)

        val precreatingCluster =
          cluster.copy(project = GoogleProject("p1"), clusterName = KubernetesClusterName("cluster2"))
        val runningCluster =
          cluster.copy(project = GoogleProject("p2"), clusterName = KubernetesClusterName("cluster3"))

        for {
          _ <- insertK8sCluster(cluster, "DELETED")
          _ <- insertK8sCluster(precreatingCluster, "PRECREATING")
          _ <- insertK8sCluster(runningCluster, "RUNNING")
          d <- dbReader.getk8sClustersToDeleteCandidate.compile.toList
        } yield {
          d.map(_.kubernetesClusterId) should contain theSameElementsAs List(precreatingCluster, runningCluster)
        }
      }
      res.unsafeRunSync()
    }
  }

  it should "update disk properly" in {
    forAll { (disk: Disk) =>
      val res = transactorResource.use { implicit xa =>
        val dbReader = DbReader.impl(xa)
        for {
          id <- insertDisk(disk)
          _ <- dbReader.updateDiskStatus(id)
          status <- getDiskStatus(id)
        } yield status shouldBe ("Deleted")
      }
      res.unsafeRunSync()
    }
  }

  it should "update k8s cluster properly" in {
    forAll { (cluster: KubernetesClusterId) =>
      val res = transactorResource.use { implicit xa =>
        val dbReader = DbReader.impl(xa)
        for {
          id <- insertK8sCluster(cluster)
          _ <- dbReader.updateK8sClusterStatus(id)
          status <- getK8sClusterStatus(id)
        } yield status shouldBe ("DELETED")
      }
      res.unsafeRunSync()
    }
  }

  it should "update nodepool status properly" in {
    forAll { (cluster: KubernetesClusterId) =>
      val res = transactorResource.use { implicit xa =>
        for {
          clusterId <- insertK8sCluster(cluster)
          nodepoolId <- insertNodepool(clusterId, "nodepool1", false)
          _ <- DbReader.updateNodepoolStatus(nodepoolId, "ERROR").run.transact(xa)
          status <- getNodepoolStatus(nodepoolId)
        } yield status shouldBe ("ERROR")
      }
      res.unsafeRunSync()
    }
  }

  it should "update App status properly" in {
    forAll { (cluster: KubernetesClusterId, disk: Disk) =>
      val res = transactorResource.use { implicit xa =>
        for {
          diskId <- insertDisk(disk)
          clusterId <- insertK8sCluster(cluster)
          nodepoolId <- insertNodepool(clusterId, "nodepool1", false)
          namespaceId <- insertNamespace(clusterId, NamespaceName("ns1"))
          appId <- insertApp(nodepoolId, namespaceId, "app1", diskId)
          _ <- DbReader.updateAppStatusForNodepoolId(nodepoolId, "DELETED").run.transact(xa)
          status <- getAppStatus(appId)
        } yield status shouldBe ("DELETED")
      }
      res.unsafeRunSync()
    }
  }

  it should "update nodepool and app status properly" in {
    forAll { (cluster: KubernetesClusterId, disk: Disk) =>
      val res = transactorResource.use { implicit xa =>
        val dbReader = DbReader.impl(xa)

        for {
          diskId <- insertDisk(disk)
          clusterId <- insertK8sCluster(cluster)
          nodepoolId <- insertNodepool(clusterId, "nodepool1", false)
          namespaceId <- insertNamespace(clusterId, NamespaceName("ns1"))
          appId <- insertApp(nodepoolId, namespaceId, "app1", diskId)
          _ <- dbReader.updateNodepoolAndAppStatus(nodepoolId, "DELETED")
          appStatus <- getAppStatus(appId)
          nodepoolStatus <- getNodepoolStatus(nodepoolId)
        } yield {
          appStatus shouldBe ("DELETED")
          nodepoolStatus shouldBe ("DELETED")
        }
      }
      res.unsafeRunSync()
    }
  }
}

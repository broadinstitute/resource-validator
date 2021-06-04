package com.broadinstitute.dsp
package resourceValidator

import com.broadinstitute.dsp.DBTestHelper.{insertK8sCluster, _}
import com.broadinstitute.dsp.Generators._
import doobie.scalatest.IOChecker
import org.broadinstitute.dsde.workbench.google2.GKEModels.{KubernetesClusterId, KubernetesClusterName}
import org.broadinstitute.dsde.workbench.model.google.GoogleProject
import org.scalatest.flatspec.AnyFlatSpec

class DbReaderGetDeletedAndErroredKubernetesClustersSpec extends AnyFlatSpec with CronJobsTestSuite with IOChecker {
  implicit val config = ConfigSpec.config.database
  val transactor = yoloTransactor

  it should "detect kubernetes clusters that are Deleted or Errored in the Leo DB" taggedAs DbTest in {
    forAll { (cluster: KubernetesClusterId) =>
      val res = transactorResource.use { implicit xa =>
        val dbReader = DbReader.impl(xa)

        val cluster2 =
          cluster.copy(project = GoogleProject("project2"))
        val cluster3 =
          cluster.copy(project = GoogleProject("project3"))
        val cluster4 =
          cluster.copy(project = GoogleProject("project4"))
        val cluster5 =
          cluster.copy(project = GoogleProject("project5"))
        val cluster6 =
          cluster.copy(project = GoogleProject("project6"))

        for {
          cluster1Id <- insertK8sCluster(cluster, "DELETED")
          cluster2Id <- insertK8sCluster(cluster2, "DELETED")
          cluster3Id <- insertK8sCluster(cluster3, "ERROR")
          _ <- insertK8sCluster(cluster4, "RUNNING")
          _ <- insertK8sCluster(cluster5, "PROVISIONING")
          _ <- insertK8sCluster(cluster6, "PRE-CREATING")

          cluster1Name <- getK8sClusterName(cluster1Id)
          cluster2Name <- getK8sClusterName(cluster2Id)
          cluster3Name <- getK8sClusterName(cluster3Id)

          clustersToDelete <- dbReader.getDeletedAndErroredKubernetesClusters.compile.toList
        } yield clustersToDelete.map(_.clusterName) shouldBe List(KubernetesClusterName(cluster1Name),
                                                                  KubernetesClusterName(cluster2Name),
                                                                  KubernetesClusterName(cluster3Name)
        )
      }
      res.unsafeRunSync()
    }
  }
}

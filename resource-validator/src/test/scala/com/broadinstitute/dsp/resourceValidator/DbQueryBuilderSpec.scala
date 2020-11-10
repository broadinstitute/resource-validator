package com.broadinstitute.dsp
package resourceValidator

import com.broadinstitute.dsp.DBTestHelper._
import doobie.scalatest.IOChecker
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
class DbQueryBuilderSpec extends AnyFlatSpec with CronJobsTestSuite with IOChecker {
  implicit val config = ConfigSpec.config.database
  val transactor = yoloTransactor

  it should "build deletedDisksQuery properly" in {
    check(DbReader.deletedDisksQuery)
  }

  it should "build initBucketsToDeleteQuery properly" in {
    check(DbReader.initBucketsToDeleteQuery)
  }

  it should "build deletedRuntimeQuery properly" in {
    check(DbReader.deletedRuntimeQuery)
  }

  it should "build erroredRuntimeQuery properly" in {
    check(DbReader.erroredRuntimeQuery)
  }

  it should "build kubernetesClustersToDeleteQuery properly" in {
    check(DbReader.kubernetesClustersToDeleteQuery)
  }

  it should "build deletedAndErroredKubernetesClusterQuery properly" in {
    check(DbReader.deletedAndErroredKubernetesClusterQuery)
  }

  it should "build deletedAndErroredNodepoolQuery properly" in {
    check(DbReader.deletedAndErroredNodepoolQuery)
  }
}

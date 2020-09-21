package com.broadinstitute.dsp
package zombieMonitor

import com.broadinstitute.dsp.DBTestHelper._
import com.broadinstitute.dsp.Generators._
import doobie.scalatest.IOChecker
import org.broadinstitute.dsde.workbench.google2.DiskName
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

  it should "builds disksToDeleteQuery properly" in {
    check(DbReader.disksToDeleteQuery)
  }

  // This test will fail with `Parameter metadata not available for the given statement`
  // This works fine for real, but doesn't work `check` due to limited support for metadata from mysql
  it should "builds updateDiskStatusQuery properly" ignore {
    check(DbReader.updateDiskStatusQuery(82))
  }

  it should "read a disk properly" in {
    forAll { (disk: Disk) =>
      val res = transactorResource.use { xa =>
        val dbReader = DbReader.impl(xa)

        val creatingDisk = disk.copy(diskName = DiskName("disk2"))
        val readyDisk = disk.copy(diskName = DiskName("disk3"))

        for {
          _ <- insertDisk(xa)(disk, "Deleted")
          _ <- insertDisk(xa)(creatingDisk, "Creating")
          _ <- insertDisk(xa)(readyDisk)
          d <- dbReader.getDisksToDeleteCandidate.compile.toList
        } yield {
          d should contain theSameElementsAs List(creatingDisk, readyDisk)
        }
      }
      res.unsafeRunSync()
    }
  }

  it should "update disk properly" in {
    forAll { (disk: Disk) =>
      val res = transactorResource.use { xa =>
        val dbReader = DbReader.impl(xa)
        for {
          id <- insertDisk(xa)(disk)
          _ <- dbReader.updateDiskStatus(id)
          status <- getDiskStatus(xa)(id)
        } yield status shouldBe ("Deleted")
      }
      res.unsafeRunSync()
    }
  }
}
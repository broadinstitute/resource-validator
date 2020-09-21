package com.broadinstitute.dsp

import cats.effect.{ContextShift, IO, Resource}
import doobie.hikari.HikariTransactor
import doobie.implicits._
import DbReaderImplicits._
import doobie.util.transactor.Transactor

object DBTestHelper {
  def yoloTransactor(implicit cs: ContextShift[IO]): Transactor[IO] = {
    val databaseConfig = Config.appConfig.toOption.get.database
    Transactor.fromDriverManager[IO](
      "com.mysql.cj.jdbc.Driver", // driver classname
      databaseConfig.url,
      databaseConfig.user,
      databaseConfig.password
    )
  }

  def transactorResource(implicit cs: ContextShift[IO]): Resource[IO, HikariTransactor[IO]] =
    for {
      config <- Resource.liftF(IO.fromEither(Config.appConfig))
      xa <- DbTransactor.init[IO](config.database)
      _ <- Resource.liftF(truncateDiskTable(xa))
    } yield xa

  def insertDiskQuery(disk: Disk, status: String) =
    sql"""insert into PERSISTENT_DISK
         (googleProject, zone, name, googleId, samResourceId, status, creator, createdDate, destroyedDate, dateAccessed, sizeGb, type, blockSizeBytes, serviceAccount, formattedBy)
         values (${disk.googleProject}, ${zoneName}, ${disk.diskName}, "fakeGoogleId", "fakeSamResourceId", ${status}, "fake@broadinstitute.org", now(), now(), now(), 50, "Standard", "4096", "pet@broadinsitute.org", "GCE")
         """.update

  def insertDisk(xa: HikariTransactor[IO])(disk: Disk, status: String = "Ready"): IO[Long] =
    insertDiskQuery(disk, status: String).withUniqueGeneratedKeys[Long]("id").transact(xa)

  def getDiskStatus(xa: HikariTransactor[IO])(diskId: Long): IO[String] =
    sql"""
         SELECT status FROM PERSISTENT_DISK where id = ${diskId}
         """.query[String].unique.transact(xa)

  private def truncateDiskTable(xa: HikariTransactor[IO]): IO[Int] =
    sql"""
         Delete from PERSISTENT_DISK
         """.update.run.transact(xa)
}

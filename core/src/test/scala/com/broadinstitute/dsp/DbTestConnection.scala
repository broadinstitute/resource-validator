package com.broadinstitute.dsp

import cats.effect.{ContextShift, IO, Resource}
import doobie.hikari.HikariTransactor
import doobie.implicits._
import DbReaderImplicits._
import doobie.util.transactor.Transactor
import org.broadinstitute.dsde.workbench.google2.GKEModels.KubernetesClusterId

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
      _ <- Resource.liftF(truncateTables(xa))
    } yield xa

  def insertDiskQuery(disk: Disk, status: String) =
    sql"""insert into PERSISTENT_DISK
         (googleProject, zone, name, googleId, samResourceId, status, creator, createdDate, destroyedDate, dateAccessed, sizeGb, type, blockSizeBytes, serviceAccount, formattedBy)
         values (${disk.googleProject}, ${zoneName}, ${disk.diskName}, "fakeGoogleId", "fakeSamResourceId", ${status}, "fake@broadinstitute.org", now(), now(), now(), 50, "Standard", "4096", "pet@broadinsitute.org", "GCE")
         """.update

  def insertDisk(xa: HikariTransactor[IO])(disk: Disk, status: String = "Ready"): IO[Long] =
    insertDiskQuery(disk, status: String).withUniqueGeneratedKeys[Long]("id").transact(xa)

  def insertK8sCluster(xa: HikariTransactor[IO])(clusterId: KubernetesClusterId, status: String = "RUNNING"): IO[Long] =
    sql"""insert into KUBERNETES_CLUSTER
         (googleProject, clusterName, location, status, creator, createdDate, destroyedDate, dateAccessed, loadBalancerIp, networkName, subNetworkName, subNetworkIpRange, region, apiServerIp, ingressChart)
         values (${clusterId.project}, ${clusterId.clusterName}, ${clusterId.location}, ${status}, "fake@broadinstitute.org", now(), now(), now(), "0.0.0.1", "network", "subnetwork", "0.0.0.1/20", ${regionName}, "35.202.56.6", "stable/nginx-ingress-1.41.3")
         """.update.withUniqueGeneratedKeys[Long]("id").transact(xa)

  def getDiskStatus(xa: HikariTransactor[IO])(diskId: Long): IO[String] =
    sql"""
         SELECT status FROM PERSISTENT_DISK where id = ${diskId}
         """.query[String].unique.transact(xa)

  def getK8sClusterStatus(xa: HikariTransactor[IO])(id: Long): IO[String] =
    sql"""
         SELECT status FROM KUBERNETES_CLUSTER where id = ${id}
         """.query[String].unique.transact(xa)

  private def truncateTables(xa: HikariTransactor[IO]): IO[Unit] = {
    val res = for {
      _ <- sql"Delete from PERSISTENT_DISK".update.run
      _ <- sql"Delete from NODEPOOL".update.run
      _ <- sql"Delete from KUBERNETES_CLUSTER".update.run
    } yield ()
    res.transact(xa)
  }
}

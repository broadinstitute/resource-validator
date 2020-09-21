package com.broadinstitute.dsp
package zombieMonitor

import java.util.UUID

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import cats.mtl.ApplicativeAsk
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.{GoogleDiskService, GoogleStorageService}
import org.broadinstitute.dsde.workbench.model.TraceId

object ZombieMonitor {
  def run[F[_]: ConcurrentEffect: Parallel](isDryRun: Boolean, ifRunAll: Boolean, ifRunCheckDeletedRuntimes: Boolean)(
    implicit T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
    implicit val traceId = ApplicativeAsk.const(TraceId(UUID.randomUUID()))

    for {
      config <- Stream.fromEither(Config.appConfig)
      deps <- Stream.resource(initDependencies(config))
      deletedRuntimeChecker = DeletedDiskChecker.impl(deps.dbReader, deps.diskCheckerDeps)
      deleteRuntimeCheckerProcess = if (ifRunAll || ifRunCheckDeletedRuntimes)
        Stream.eval(deletedRuntimeChecker.run(isDryRun))
      else Stream.empty
      processes = Stream(deleteRuntimeCheckerProcess).covary[F] //TODO: add more check

      _ <- processes.parJoin(1)
    } yield ExitCode.Success
  }.drain

  private def initDependencies[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig
  ): Resource[F, ZombieMonitorDeps[F]] =
    for {
      blocker <- Blocker[F]
      blockerBound <- Resource.liftF(Semaphore[F](250))
      runtimeChecker <- RuntimeCheckerDeps.init(appConfig, blocker, blockerBound)
      diskService <- GoogleDiskService.resource(appConfig.pathToCredential.toString, blocker, blockerBound)
      storageService <- GoogleStorageService.resource(appConfig.pathToCredential.toString,
                                                      blocker,
                                                      Some(blockerBound),
                                                      None)
      fixedThreadPool <- ExecutionContexts.fixedThreadPool(100)
      cachedThreadPool <- ExecutionContexts.cachedThreadPool
      xa <- HikariTransactor.newHikariTransactor[F](
        "com.mysql.cj.jdbc.Driver", // driver classname
        appConfig.database.url,
        appConfig.database.user,
        appConfig.database.password,
        fixedThreadPool, // await connection here
        Blocker.liftExecutionContext(cachedThreadPool)
      )
    } yield {
      val dbReader = DbReader.impl(xa)
      val checkRunnerDeps = CheckRunnerDeps(appConfig.reportDestinationBucket, storageService)
      ZombieMonitorDeps(DiskCheckerDeps(checkRunnerDeps, diskService), dbReader, blocker)
    }
}

final case class ZombieMonitorDeps[F[_]](
  diskCheckerDeps: DiskCheckerDeps[F],
  dbReader: DbReader[F],
  blocker: Blocker
)

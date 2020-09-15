package com.broadinstitute.dsp
package zombieMonitor

import java.util.UUID

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, Concurrent, ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import cats.mtl.ApplicativeAsk
import com.google.auth.oauth2.ServiceAccountCredentials
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.google2.{GoogleComputeService, GoogleDataprocService, GoogleStorageService}
import org.broadinstitute.dsde.workbench.model.TraceId

import scala.jdk.CollectionConverters._

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
      deletedRuntimeChecker = DeletedRuntimeChecker.iml(deps.dbReader, deps.runtimeCheckerDeps)
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
      runtimeChecker <- RuntimeChecker.initRuntimeCheckerDeps(appConfig, blocker)
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
      ZombieMonitorDeps(runtimeChecker, dbReader, blocker)
    }
}

final case class ZombieMonitorDeps[F[_]](
  runtimeCheckerDeps: RuntimeCheckerDeps[F],
  dbReader: DbReader[F],
  blocker: Blocker
)

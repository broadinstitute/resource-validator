package com.broadinstitute.dsp
package resourceValidator

import java.util.UUID

import cats.Parallel
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import cats.mtl.ApplicativeAsk
import com.broadinstitute.dsp.{AnomalyChecker, AnomalyCheckerDeps, AppConfig, Config}
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.model.TraceId

object ResourceValidator {
  def run[F[_]: ConcurrentEffect: Parallel](isDryRun: Boolean,
                                            ifRunAll: Boolean,
                                            ifRunCheckDeletedRuntimes: Boolean,
                                            ifRunCheckErroredRuntimes: Boolean)(
    implicit T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
    implicit val traceId = ApplicativeAsk.const(TraceId(UUID.randomUUID()))

    for {
      config <- Stream.fromEither(Config.appConfig)
      deps <- Stream.resource(initDependencies(config))
      deletedRuntimeChecker = DeletedRuntimeChecker.impl(deps.dbReader, deps.runtimeCheckerDeps)
      deleteRuntimeCheckerProcess = if (ifRunAll || ifRunCheckDeletedRuntimes)
        Stream.eval(deletedRuntimeChecker.run(isDryRun))
      else Stream.empty
      errorRuntimeCheckerProcess = if (ifRunAll || ifRunCheckErroredRuntimes)
        Stream.eval(ErroredRuntimeChecker.iml(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty
      processes = Stream(deleteRuntimeCheckerProcess, errorRuntimeCheckerProcess).covary[F] //TODO: add more check

      _ <- processes.parJoin(2)
    } yield ExitCode.Success
  }.drain

  private def initDependencies[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig
  ): Resource[F, ResourcevalidatorServerDeps[F]] =
    for {
      blocker <- Blocker[F]
      runtimeCheckerDeps <- AnomalyChecker.initAnomalyCheckerDeps(appConfig, blocker)
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
      ResourcevalidatorServerDeps(runtimeCheckerDeps, dbReader, blocker)
    }
}

final case class ResourcevalidatorServerDeps[F[_]](
  runtimeCheckerDeps: AnomalyCheckerDeps[F],
  dbReader: DbReader[F],
  blocker: Blocker
)

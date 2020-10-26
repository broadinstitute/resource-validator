package com.broadinstitute.dsp
package zombieMonitor

import java.util.UUID

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import cats.mtl.ApplicativeAsk
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.{GKEService, GoogleDiskService}
import org.broadinstitute.dsde.workbench.model.TraceId

object ZombieMonitor {
  def run[F[_]: ConcurrentEffect: Parallel](isDryRun: Boolean,
                                            shouldRunAll: Boolean,
                                            shouldCheckDeletedRuntimes: Boolean,
                                            shouldCheckDeletedDisks: Boolean,
                                            shouldCheckDeletedK8sClusters: Boolean,
                                            shouldCheckDeletedOrErroredNodepool: Boolean)(
    implicit T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
    implicit val traceId = ApplicativeAsk.const(TraceId(UUID.randomUUID()))

    for {
      config <- Stream.fromEither(Config.appConfig)
      deps <- Stream.resource(initDependencies(config))

      deleteDiskCheckerProcess = if (shouldRunAll || shouldCheckDeletedDisks)
        Stream.eval(DeletedDiskChecker.impl(deps.dbReader, deps.diskCheckerDeps).run(isDryRun))
      else Stream.empty
      deleteRuntimeCheckerProcess = if (shouldRunAll || shouldCheckDeletedRuntimes)
        Stream.eval(DeletedOrErroredRuntimeChecker.impl(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty
      deletek8sClusterCheckerProcess = if (shouldRunAll || shouldCheckDeletedK8sClusters)
        Stream.eval(
          DeletedKubernetesClusterChecker.impl(deps.dbReader, deps.kubernetesClusterCheckerDeps).run(isDryRun)
        )
      else Stream.empty
      deleteOrErroredNodepoolCheckerProcess = if (shouldRunAll || shouldCheckDeletedOrErroredNodepool)
        Stream.eval(
          DeletedOrErroredNodepoolChecker.impl(deps.dbReader, deps.kubernetesClusterCheckerDeps).run(isDryRun)
        )
      else Stream.empty

      processes = Stream(deleteDiskCheckerProcess,
                         deleteRuntimeCheckerProcess,
                         deletek8sClusterCheckerProcess,
                         deleteOrErroredNodepoolCheckerProcess).covary[F]
      _ <- processes.parJoin(2)
    } yield ExitCode.Success
  }.drain

  private def initDependencies[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig
  ): Resource[F, ZombieMonitorDeps[F]] =
    for {
      blocker <- Blocker[F]
      blockerBound <- Resource.liftF(Semaphore[F](250))
      runtimeCheckerDeps <- RuntimeCheckerDeps.init(appConfig.runtimeCheckerConfig, blocker, blockerBound)
      diskService <- GoogleDiskService.resource(appConfig.pathToCredential.toString, blocker, blockerBound)
      gkeService <- GKEService.resource(appConfig.pathToCredential, blocker, blockerBound)
      xa <- DbTransactor.init(appConfig.database)
    } yield {
      val dbReader = DbReader.impl(xa)
      val checkRunnerDeps = runtimeCheckerDeps.checkRunnerDeps
      val k8sCheckerDeps = KubernetesClusterCheckerDeps(checkRunnerDeps, gkeService)
      ZombieMonitorDeps(DiskCheckerDeps(checkRunnerDeps, diskService),
                        runtimeCheckerDeps,
                        k8sCheckerDeps,
                        dbReader,
                        blocker)
    }
}

final case class ZombieMonitorDeps[F[_]](
  diskCheckerDeps: DiskCheckerDeps[F],
  runtimeCheckerDeps: RuntimeCheckerDeps[F],
  kubernetesClusterCheckerDeps: KubernetesClusterCheckerDeps[F],
  dbReader: DbReader[F],
  blocker: Blocker
)

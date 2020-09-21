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
import org.broadinstitute.dsde.workbench.google2.{GKEService, GoogleDiskService, GoogleStorageService}
import org.broadinstitute.dsde.workbench.model.TraceId

object ZombieMonitor {
  def run[F[_]: ConcurrentEffect: Parallel](isDryRun: Boolean,
                                            ifRunAll: Boolean,
                                            ifRunCheckDeletedRuntimes: Boolean,
                                            ifRunCheckDeletedK8sClusters: Boolean)(
    implicit T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
    implicit val traceId = ApplicativeAsk.const(TraceId(UUID.randomUUID()))

    for {
      config <- Stream.fromEither(Config.appConfig)
      deps <- Stream.resource(initDependencies(config))

      deleteRuntimeCheckerProcess = if (ifRunAll || ifRunCheckDeletedRuntimes)
        Stream.eval(DeletedDiskChecker.impl(deps.dbReader, deps.diskCheckerDeps).run(isDryRun))
      else Stream.empty
      deletek8sClusterCheckerProcess = if (ifRunAll || ifRunCheckDeletedK8sClusters)
        Stream.eval(
          DeletedKubernetesClusterChecker.impl(deps.dbReader, deps.kubernetesClusterCheckerDeps).run(isDryRun)
        )
      else Stream.empty

      processes = Stream(deleteRuntimeCheckerProcess, deletek8sClusterCheckerProcess).covary[F]
      _ <- processes.parJoin(2)
    } yield ExitCode.Success
  }.drain

  private def initDependencies[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig
  ): Resource[F, ZombieMonitorDeps[F]] =
    for {
      blocker <- Blocker[F]
      blockerBound <- Resource.liftF(Semaphore[F](250))
      diskService <- GoogleDiskService.resource(appConfig.pathToCredential.toString, blocker, blockerBound)
      storageService <- GoogleStorageService.resource(appConfig.pathToCredential.toString,
                                                      blocker,
                                                      Some(blockerBound),
                                                      None)
      gkeService <- GKEService.resource(appConfig.pathToCredential, blocker, blockerBound)
      xa <- DbTransactor.init(appConfig.database)
    } yield {
      val dbReader = DbReader.impl(xa)
      val checkRunnerDeps = CheckRunnerDeps(appConfig.reportDestinationBucket, storageService)
      val k8sCheckerDeps = KubernetesClusterCheckerDeps(checkRunnerDeps, gkeService)
      ZombieMonitorDeps(DiskCheckerDeps(checkRunnerDeps, diskService), k8sCheckerDeps, dbReader, blocker)
    }
}

final case class ZombieMonitorDeps[F[_]](
  diskCheckerDeps: DiskCheckerDeps[F],
  kubernetesClusterCheckerDeps: KubernetesClusterCheckerDeps[F],
  dbReader: DbReader[F],
  blocker: Blocker
)

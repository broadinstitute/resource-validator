package com.broadinstitute.dsp
package resourceValidator

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import cats.mtl.Ask
import com.google.pubsub.v1.ProjectTopicName
import fs2.Stream
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.{GKEService, GoogleDiskService, GooglePublisher, PublisherConfig}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetrics

import java.util.UUID

object ResourceValidator {
  def run[F[_]: ConcurrentEffect: Parallel](isDryRun: Boolean,
                                            shouldCheckAll: Boolean,
                                            shouldCheckDeletedRuntimes: Boolean,
                                            shouldCheckErroredRuntimes: Boolean,
                                            shouldCheckStoppedRuntimes: Boolean,
                                            shouldCheckDeletedKubernetesCluster: Boolean,
                                            shouldCheckDeletedNodepool: Boolean,
                                            shouldCheckDeletedDisks: Boolean,
                                            shouldCheckInitBuckets: Boolean,
                                            shouldCheckDataprocWorkers: Boolean
  )(implicit
    T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
    implicit val traceId = Ask.const(TraceId(UUID.randomUUID()))

    for {
      config <- Stream.fromEither(Config.appConfig)
      deps <- Stream.resource(initDependencies(config))
      checkRunnerDep = deps.runtimeCheckerDeps.checkRunnerDeps

      deleteRuntimeCheckerProcess = if (shouldCheckAll || shouldCheckDeletedRuntimes)
        Stream.eval(DeletedRuntimeChecker.impl(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty

      deleteDiskCheckerProcess = if (shouldCheckAll || shouldCheckDeletedDisks)
        Stream.eval(DeletedDiskChecker.impl[F](deps.dbReader, deps.deletedDiskCheckerDeps).run(isDryRun))
      else Stream.empty

      errorRuntimeCheckerProcess = if (shouldCheckAll || shouldCheckErroredRuntimes)
        Stream.eval(ErroredRuntimeChecker.iml(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty
      deleteKubernetesClusterCheckerProcess = if (shouldCheckAll || shouldCheckDeletedKubernetesCluster)
        Stream.eval(
          DeletedOrErroredKubernetesClusterChecker.impl(deps.dbReader, deps.kubernetesClusterCheckerDeps).run(isDryRun)
        )
      else Stream.empty
      deleteNodepoolCheckerProcess = if (shouldCheckAll || shouldCheckDeletedNodepool)
        Stream.eval(
          DeletedOrErroredNodepoolChecker.impl(deps.dbReader, deps.nodepoolCheckerDeps).run(isDryRun)
        )
      else Stream.empty

      stoppedRuntimeCheckerProcess = if (shouldCheckAll || shouldCheckStoppedRuntimes)
        Stream.eval(StoppedRuntimeChecker.iml(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty

      removeInitBuckets = if (shouldCheckAll || shouldCheckInitBuckets)
        Stream.eval(InitBucketChecker.impl(deps.dbReader, checkRunnerDep).run(isDryRun))
      else Stream.empty

      workerProcess = if (shouldCheckAll || shouldCheckDataprocWorkers)
        Stream.eval(DataprocWorkerChecker.impl(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty

      processes = Stream(
        deleteRuntimeCheckerProcess,
        errorRuntimeCheckerProcess,
        stoppedRuntimeCheckerProcess,
        deleteDiskCheckerProcess,
        removeInitBuckets,
        deleteKubernetesClusterCheckerProcess,
        deleteNodepoolCheckerProcess,
        workerProcess
      ).covary[F]

      _ <- processes.parJoin(8) // Number of checkers in 'processes'
    } yield ExitCode.Success
  }.drain

  private def initDependencies[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig
  ): Resource[F, ResourcevalidatorServerDeps[F]] =
    for {
      blocker <- Blocker[F]
      blockerBound <- Resource.eval(Semaphore[F](250))
      metrics <- OpenTelemetryMetrics.resource(appConfig.pathToCredential, "leonardo-cron-jobs", blocker)
      runtimeCheckerDeps <- RuntimeCheckerDeps.init(appConfig.runtimeCheckerConfig, blocker, metrics, blockerBound)
      diskService <- GoogleDiskService.resource(appConfig.pathToCredential.toString, blocker, blockerBound)
      publisherConfig = PublisherConfig(
        appConfig.pathToCredential.toString,
        ProjectTopicName.of(appConfig.leonardoPubsub.googleProject.value, appConfig.leonardoPubsub.topicName)
      )
      gkeService <- GKEService.resource(appConfig.pathToCredential, blocker, blockerBound)
      googlePublisher <- GooglePublisher.resource[F](publisherConfig)
      xa <- DbTransactor.init(appConfig.database)
    } yield {
      val checkRunnerDeps = runtimeCheckerDeps.checkRunnerDeps
      val diskCheckerDeps = DiskCheckerDeps(checkRunnerDeps, diskService)
      val kubernetesClusterCheckerDeps = KubernetesClusterCheckerDeps(checkRunnerDeps, gkeService)
      val nodepoolCheckerDeps = NodepoolCheckerDeps(checkRunnerDeps, gkeService, googlePublisher)
      val dbReader = DbReader.impl(xa)
      ResourcevalidatorServerDeps(runtimeCheckerDeps,
                                  diskCheckerDeps,
                                  kubernetesClusterCheckerDeps,
                                  nodepoolCheckerDeps,
                                  dbReader,
                                  blocker
      )
    }
}

final case class ResourcevalidatorServerDeps[F[_]](
  runtimeCheckerDeps: RuntimeCheckerDeps[F],
  deletedDiskCheckerDeps: DiskCheckerDeps[F],
  kubernetesClusterCheckerDeps: KubernetesClusterCheckerDeps[F],
  nodepoolCheckerDeps: NodepoolCheckerDeps[F],
  dbReader: DbReader[F],
  blocker: Blocker
)

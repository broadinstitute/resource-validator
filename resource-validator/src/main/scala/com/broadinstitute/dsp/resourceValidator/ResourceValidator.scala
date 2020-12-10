package com.broadinstitute.dsp
package resourceValidator

import java.util.UUID

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import cats.mtl.Ask
import com.google.pubsub.v1.ProjectTopicName
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.{GKEService, GoogleDiskService, GooglePublisher, PublisherConfig}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetrics

object ResourceValidator {
  def run[F[_]: ConcurrentEffect: Parallel](isDryRun: Boolean,
                                            shouldRunAll: Boolean,
                                            shouldCheckDeletedRuntimes: Boolean,
                                            shouldCheckErroredRuntimes: Boolean,
                                            shouldCheckStoppedRuntimes: Boolean,
                                            shouldRunCheckDeletedKubernetesCluster: Boolean,
                                            shouldRunCheckDeletedNodepool: Boolean,
                                            shouldCheckDeletedDisks: Boolean,
                                            shouldCheckInitBuckets: Boolean)(
    implicit T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
    implicit val traceId = Ask.const(TraceId(UUID.randomUUID()))

    for {
      config <- Stream.fromEither(Config.appConfig)
      deps <- Stream.resource(initDependencies(config))
      checkRunnerDep = deps.runtimeCheckerDeps.checkRunnerDeps

      deleteRuntimeCheckerProcess = if (shouldRunAll || shouldCheckDeletedRuntimes)
        Stream.eval(DeletedRuntimeChecker.impl(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty

      deleteDiskCheckerProcess = if (shouldRunAll || shouldCheckDeletedDisks)
        Stream.eval(DeletedDiskChecker.impl[F](deps.dbReader, deps.deletedDiskCheckerDeps).run(isDryRun))
      else Stream.empty

      errorRuntimeCheckerProcess = if (shouldRunAll || shouldCheckErroredRuntimes)
        Stream.eval(ErroredRuntimeChecker.iml(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty
      deleteKubernetesClusterCheckerProcess = if (shouldRunAll || shouldRunCheckDeletedKubernetesCluster)
        Stream.eval(
          DeletedOrErroredKubernetesClusterChecker.impl(deps.dbReader, deps.kubernetesClusterCheckerDeps).run(isDryRun)
        )
      else Stream.empty
      deleteNodepoolCheckerProcess = if (shouldRunAll || shouldRunCheckDeletedNodepool)
        Stream.eval(
          DeletedOrErroredNodepoolChecker.impl(deps.dbReader, deps.kubernetesClusterCheckerDeps).run(isDryRun)
        )
      else Stream.empty

      stoppedRuntimeCheckerProcess = if (shouldRunAll || shouldCheckStoppedRuntimes)
        Stream.eval(StoppedRuntimeChecker.iml(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty

      removeStagingBucketProcess = if (shouldRunAll)
        Stream.eval(BucketRemover.impl(deps.dbReader, checkRunnerDep).run(isDryRun))
      else Stream.empty

      removeKubernetesClusters = if (shouldRunAll)
        Stream.eval(KubernetesClusterRemover.impl(deps.dbReader, deps.kubernetesClusterRemoverDeps).run(isDryRun))
      else Stream.empty

      removeInitBuckets = if (shouldRunAll || shouldCheckInitBuckets)
        Stream.eval(InitBucketChecker.impl(deps.dbReader, checkRunnerDep).run(isDryRun))
      else Stream.empty

      processes = Stream(
        deleteRuntimeCheckerProcess,
        errorRuntimeCheckerProcess,
        stoppedRuntimeCheckerProcess,
        removeStagingBucketProcess,
        deleteDiskCheckerProcess,
        removeInitBuckets,
        removeKubernetesClusters,
        deleteKubernetesClusterCheckerProcess,
        deleteNodepoolCheckerProcess
      ).covary[F]

      _ <- processes.parJoin(9) // Update this number as we add more streams
    } yield ExitCode.Success
  }.drain

  private def initDependencies[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig
  ): Resource[F, ResourcevalidatorServerDeps[F]] =
    for {
      blocker <- Blocker[F]
      blockerBound <- Resource.liftF(Semaphore[F](250))
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
      val kubernetesClusterToRemoveDeps = KubernetesClusterRemoverDeps(googlePublisher, checkRunnerDeps)
      val kubernetesClusterCheckerDeps = KubernetesClusterCheckerDeps(checkRunnerDeps, gkeService)
      val dbReader = DbReader.impl(xa)
      ResourcevalidatorServerDeps(runtimeCheckerDeps,
                                  diskCheckerDeps,
                                  kubernetesClusterToRemoveDeps,
                                  kubernetesClusterCheckerDeps,
                                  dbReader,
                                  blocker)
    }
}

final case class ResourcevalidatorServerDeps[F[_]](
  runtimeCheckerDeps: RuntimeCheckerDeps[F],
  deletedDiskCheckerDeps: DiskCheckerDeps[F],
  kubernetesClusterRemoverDeps: KubernetesClusterRemoverDeps[F],
  kubernetesClusterCheckerDeps: KubernetesClusterCheckerDeps[F],
  dbReader: DbReader[F],
  blocker: Blocker
)

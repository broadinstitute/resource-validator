package com.broadinstitute.dsp
package resourceValidator

import java.util.UUID

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import cats.mtl.ApplicativeAsk
import com.google.pubsub.v1.ProjectTopicName
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.{
  GoogleDiskService,
  GooglePublisher,
  GoogleTopicAdminInterpreter,
  PublisherConfig
}
import org.broadinstitute.dsde.workbench.model.TraceId

object ResourceValidator {
  def run[F[_]: ConcurrentEffect: Parallel](isDryRun: Boolean,
                                            shouldRunAll: Boolean,
                                            shouldRunCheckDeletedRuntimes: Boolean,
                                            shouldRunCheckErroredRuntimes: Boolean,
                                            shouldRunCheckDeletedDisks: Boolean,
                                            shouldRunCheckInitBuckets: Boolean)(
    implicit T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
    implicit val traceId = ApplicativeAsk.const(TraceId(UUID.randomUUID()))

    for {
      config <- Stream.fromEither(Config.appConfig)
      deps <- Stream.resource(initDependencies(config))
      deleteRuntimeCheckerProcess = if (shouldRunAll || shouldRunCheckDeletedRuntimes)
        Stream.eval(DeletedRuntimeChecker.impl(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty
      deleteDiskCheckerProcess = if (shouldRunAll || shouldRunCheckDeletedDisks)
        Stream.eval(DeletedDiskChecker.impl[F](deps.dbReader, deps.deletedDiskCheckerDeps).run(isDryRun))
      else Stream.empty
      errorRuntimeCheckerProcess = if (shouldRunAll || shouldRunCheckErroredRuntimes)
        Stream.eval(ErroredRuntimeChecker.iml(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty

      checkRunnerDep = CheckRunnerDeps(deps.runtimeCheckerDeps.reportDestinationBucket,
                                       deps.runtimeCheckerDeps.storageService)
      removeStagingBucketProcess = if (shouldRunAll)
        Stream.eval(BucketRemover.impl(deps.dbReader, checkRunnerDep).run(isDryRun))
      else Stream.empty

      removeKubernetesClusters = if (shouldRunAll)
        Stream.eval(KubernetesClusterRemover.impl(deps.dbReader, deps.kubernetesClusterRemoverDeps).run(isDryRun))
      else Stream.empty

      removeInitBuckets = if (shouldRunAll || shouldRunCheckInitBuckets)
        Stream.eval(InitBucketChecker.impl(deps.dbReader, checkRunnerDep).run(isDryRun))
      else Stream.empty

      processes = Stream(
        // TODO Uncomment out when done dry-running
//        deleteRuntimeCheckerProcess,
//        errorRuntimeCheckerProcess,
//        removeStagingBucketProcess,
//        deleteDiskCheckerProcess,
//        removeInitBuckets,
        removeKubernetesClusters
      ).covary[F]

      _ <- processes.parJoin(6) // Update this number as we add more streams
    } yield ExitCode.Success
  }.drain

  private def initDependencies[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig
  ): Resource[F, ResourcevalidatorServerDeps[F]] =
    for {
      blocker <- Blocker[F]
      blockerBound <- Resource.liftF(Semaphore[F](250))
      checkerDeps <- RuntimeCheckerDeps.init(appConfig, blocker, blockerBound)
      diskService <- GoogleDiskService.resource(appConfig.pathToCredential.toString, blocker, blockerBound)
      publisherConfig = PublisherConfig(
        appConfig.pathToCredential.toString,
        ProjectTopicName.of(appConfig.leonardoPubsub.googleProject.value, appConfig.leonardoPubsub.topicName),
        GoogleTopicAdminInterpreter.defaultRetryConfig
      )
      googlePublisher <- GooglePublisher.resource[F](publisherConfig)
      xa <- DbTransactor.init(appConfig.database)
    } yield {
      val checkRunnerDeps = CheckRunnerDeps[F](appConfig.reportDestinationBucket, checkerDeps.storageService)
      val diskCheckerDeps = DiskCheckerDeps(checkRunnerDeps, diskService)
      val kubernetesClusterToRemoveDeps = KubernetesClusterRemoverDeps(googlePublisher, checkRunnerDeps)
      val dbReader = DbReader.impl(xa)
      ResourcevalidatorServerDeps(checkerDeps, diskCheckerDeps, kubernetesClusterToRemoveDeps, dbReader, blocker)
    }
}

final case class ResourcevalidatorServerDeps[F[_]](
  runtimeCheckerDeps: RuntimeCheckerDeps[F],
  deletedDiskCheckerDeps: DiskCheckerDeps[F],
  kubernetesClusterRemoverDeps: KubernetesClusterRemoverDeps[F],
  dbReader: DbReader[F],
  blocker: Blocker
)

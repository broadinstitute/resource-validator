package com.broadinstitute.dsp
package resourceValidator

import java.util.UUID

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import cats.mtl.ApplicativeAsk
import fs2.Stream
import io.chrisdavenport.log4cats.StructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.broadinstitute.dsde.workbench.google2.GoogleDiskService
import org.broadinstitute.dsde.workbench.model.TraceId

object ResourceValidator {
  def run[F[_]: ConcurrentEffect: Parallel](isDryRun: Boolean,
                                            ifRunAll: Boolean,
                                            ifRunCheckDeletedRuntimes: Boolean,
                                            ifRunCheckErroredRuntimes: Boolean,
                                            ifRunCheckDeletedDisks: Boolean)(
    implicit T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
    implicit val traceId = ApplicativeAsk.const(TraceId(UUID.randomUUID()))

    for {
      config <- Stream.fromEither(Config.appConfig)
      deps <- Stream.resource(initDependencies(config))
      deleteRuntimeCheckerProcess = if (ifRunAll || ifRunCheckDeletedRuntimes)
        Stream.eval(DeletedRuntimeChecker.impl(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty
      deleteDiskCheckerProcess = if (ifRunAll || ifRunCheckDeletedDisks)
        Stream.eval(DeletedDiskChecker.impl[F](deps.dbReader, deps.deletedDiskCheckerDeps).run(isDryRun))
      else Stream.empty
      errorRuntimeCheckerProcess = if (ifRunAll || ifRunCheckErroredRuntimes)
        Stream.eval(ErroredRuntimeChecker.iml(deps.dbReader, deps.runtimeCheckerDeps).run(isDryRun))
      else Stream.empty

      checkRunnerDep = CheckRunnerDeps(deps.runtimeCheckerDeps.reportDestinationBucket,
                                       deps.runtimeCheckerDeps.storageService)
      removeStagingBucketProcess = if (ifRunAll)
        Stream.eval(BucketRemover.impl(deps.dbReader, checkRunnerDep).run(isDryRun))
      else Stream.empty
      removeKubernetesClusters = if (ifRunAll)
        Stream.eval(KubernetesClusterRemover.impl(deps.dbReader, checkRunnerDep).run(isDryRun))
      else Stream.empty
      processes = Stream(
//          deleteRuntimeCheckerProcess,
//                         errorRuntimeCheckerProcess,
//                         removeStagingBucketProcess,
//                         deleteDiskCheckerProcess,
        removeKubernetesClusters
      ).covary[F] //TODO: add more check

      _ <- processes.parJoin(5) //Update this number as we add more streams
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
      xa <- DbTransactor.init(appConfig.database)
    } yield {
      val checkRunnerDeps = CheckRunnerDeps[F](appConfig.reportDestinationBucket, checkerDeps.storageService)
      val diskCheckerDeps = DiskCheckerDeps(checkRunnerDeps, diskService)
      val dbReader = DbReader.impl(xa)
      ResourcevalidatorServerDeps(checkerDeps, diskCheckerDeps, dbReader, blocker)
    }
}

final case class ResourcevalidatorServerDeps[F[_]](
  runtimeCheckerDeps: RuntimeCheckerDeps[F],
  deletedDiskCheckerDeps: DiskCheckerDeps[F],
  dbReader: DbReader[F],
  blocker: Blocker
)

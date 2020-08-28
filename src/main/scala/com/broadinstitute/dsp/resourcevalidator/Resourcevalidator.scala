package com.broadinstitute.dsp.resourcevalidator

import java.util.UUID

import cats.{Applicative, Parallel}
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

object Resourcevalidator {
  def run[F[_]: ConcurrentEffect: Parallel](isDryRun: Boolean, ifRunAll: Boolean, ifRunCheckDeletedRuntimes: Boolean)(
    implicit T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]
    implicit val traceId = ApplicativeAsk.const(TraceId(UUID.randomUUID()))

    for {
      config <- Stream.fromEither(Config.appConfig)
      deps <- Stream.resource(initDependencies(config))
      deletedRuntimeChecker = DeletedRuntimeChecker.iml(deps.deletedRuntimeCheckerDeps)
      deleteRuntimeCheckerProcess = if (ifRunAll || ifRunCheckDeletedRuntimes)
        Stream.eval(deletedRuntimeChecker.run(isDryRun))
      else Stream.empty
      processes = Stream(deleteRuntimeCheckerProcess).covary[F] //TODO: add more check
      _ <- processes.parJoin(1)
    } yield ExitCode.Success
  }.drain

  private def initDependencies[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig
  ): Resource[F, ResourcevalidatorServerDeps[F]] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile[F](appConfig.pathToCredential.toString)
      credential <- Resource.liftF(Async[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
      scopedCredential = credential.createScoped(Seq("https://www.googleapis.com/auth/cloud-platform").asJava)
      blocker <- Blocker[F]
      blockerBound <- Resource.liftF(Semaphore[F](10))
      computeService <- GoogleComputeService.fromCredential(scopedCredential,
                                                            blocker,
                                                            blockerBound,
                                                            RetryPredicates.standardRetryConfig)
      storageService <- GoogleStorageService.resource(appConfig.pathToCredential.toString,
                                                      blocker,
                                                      Some(blockerBound),
                                                      None)
      dataprocService <- GoogleDataprocService.fromCredential(scopedCredential,
                                                              blocker,
                                                              regionName,
                                                              blockerBound,
                                                              RetryPredicates.standardRetryConfig)
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
      val dbReader = DbReader.iml(xa)
      val deletedRuntimeCheckerDeps =
        DeletedRuntimeCheckerDeps(appConfig.reportDestinationBucket,
                                  computeService,
                                  storageService,
                                  dataprocService,
                                  dbReader)
      ResourcevalidatorServerDeps(deletedRuntimeCheckerDeps, blocker)
    }
}

final case class ResourcevalidatorServerDeps[F[_]](
  deletedRuntimeCheckerDeps: DeletedRuntimeCheckerDeps[F],
  blocker: Blocker
)

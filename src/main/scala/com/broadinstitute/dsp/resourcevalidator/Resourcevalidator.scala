package com.broadinstitute.dsp.resourcevalidator

import cats.effect.{Async, Blocker, ConcurrentEffect, ContextShift, ExitCode, Resource, Sync, Timer}
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.compute.v1.{InstanceClient, InstanceSettings}
import com.google.cloud.dataproc.v1.{ClusterControllerClient, ClusterControllerSettings}
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import fs2.Stream
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

import scala.jdk.CollectionConverters._

object Resourcevalidator {
  def run[F[_]: ConcurrentEffect](isDryRun: Boolean, ifRunAll: Boolean, ifRunCheckDeletedRuntimes: Boolean)(
    implicit T: Timer[F],
    C: ContextShift[F]
  ): Stream[F, Nothing] = {
    implicit def getLogger[F[_]: Sync] = Slf4jLogger.getLogger[F]

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

  private def initDependencies[F[_]: Async: ContextShift](
    appConfig: AppConfig
  ): Resource[F, ResourcevalidatorServerDeps[F]] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile[F](appConfig.pathToCredential.toString)
      credential <- Resource.liftF(Async[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
      scopedCredential = credential.createScoped(Seq("https://www.googleapis.com/auth/cloud-platform").asJava)
      credentialProvider = FixedCredentialsProvider.create(scopedCredential)
      settings = ClusterControllerSettings
        .newBuilder()
        .setEndpoint(s"${regionName}-dataproc.googleapis.com:443")
        .setCredentialsProvider(credentialProvider)
        .build()
      client <- Resource.liftF(
        Async[F].delay(
          ClusterControllerClient.create(settings)
        )
      )
      blocker <- Blocker[F]

      instanceClientSettings = InstanceSettings
        .newBuilder()
        .setCredentialsProvider(credentialProvider)
        .build()
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
      val instanceClient = InstanceClient.create(instanceClientSettings)
      val dbReader = DbReader.iml(xa)
      val deletedRuntimeCheckerDeps = DeletedRuntimeCheckerDeps(client, instanceClient, dbReader)
      ResourcevalidatorServerDeps(deletedRuntimeCheckerDeps, blocker)
    }
}

final case class ResourcevalidatorServerDeps[F[_]](
  deletedRuntimeCheckerDeps: DeletedRuntimeCheckerDeps[F],
  blocker: Blocker
)

package com.broadinstitute.dsp

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, Concurrent, ContextShift, Resource, Timer}
import com.google.auth.oauth2.ServiceAccountCredentials
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.google2.{GoogleComputeService, GoogleDataprocService, GoogleStorageService}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

import scala.jdk.CollectionConverters._

object AnomalyChecker {
  def initAnomalyCheckerDeps[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig,
    blocker: Blocker
  ): Resource[F, AnomalyCheckerDeps[F]] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile[F](appConfig.pathToCredential.toString)
      credential <- Resource.liftF(Async[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
      scopedCredential = credential.createScoped(Seq("https://www.googleapis.com/auth/cloud-platform").asJava)
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
    } yield {
      AnomalyCheckerDeps(appConfig.reportDestinationBucket, computeService, storageService, dataprocService)
    }
}

final case class Runtime(googleProject: GoogleProject, runtimeName: String, cloudService: CloudService) {
  override def toString: String = s"${googleProject.value},${runtimeName},${cloudService}"
}
final case class AnomalyCheckerDeps[F[_]](reportDestinationBucket: GcsBucketName,
                                          computeService: GoogleComputeService[F],
                                          storageService: GoogleStorageService[F],
                                          dataprocService: GoogleDataprocService[F])

package com.broadinstitute.dsp

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Async, Blocker, Concurrent, ContextShift, Resource, Timer}
import com.google.auth.oauth2.ServiceAccountCredentials
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.google2.{
  GKEService,
  GoogleComputeService,
  GoogleDataprocService,
  GoogleDiskService,
  GoogleStorageService
}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}

import scala.jdk.CollectionConverters._

object RuntimeCheckerDeps {
  def init[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    appConfig: AppConfig,
    blocker: Blocker,
    blockerBound: Semaphore[F]
  ): Resource[F, RuntimeCheckerDeps[F]] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile[F](appConfig.pathToCredential.toString)
      credential <- Resource.liftF(Async[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
      scopedCredential = credential.createScoped(Seq("https://www.googleapis.com/auth/cloud-platform").asJava)
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
      RuntimeCheckerDeps(appConfig.reportDestinationBucket, computeService, storageService, dataprocService)
    }
}

final case class Runtime(googleProject: GoogleProject, runtimeName: String, cloudService: CloudService) {
  override def toString: String = s"${googleProject.value},${runtimeName},${cloudService}"
}
final case class RuntimeCheckerDeps[F[_]](reportDestinationBucket: GcsBucketName,
                                          computeService: GoogleComputeService[F],
                                          storageService: GoogleStorageService[F],
                                          dataprocService: GoogleDataprocService[F])

final case class KubernetesClusterCheckerDeps[F[_]](checkRunnerDeps: CheckRunnerDeps[F], gkeService: GKEService[F])

final case class DiskCheckerDeps[F[_]](checkRunnerDeps: CheckRunnerDeps[F], googleDiskService: GoogleDiskService[F])
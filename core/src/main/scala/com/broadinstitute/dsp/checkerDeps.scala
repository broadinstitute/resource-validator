package com.broadinstitute.dsp

import java.nio.file.Path

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
    appConfig: RuntimeCheckerConfig,
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
      val checkRunnerDeps = CheckRunnerDeps(appConfig.reportDestinationBucket, storageService)
      RuntimeCheckerDeps(computeService, dataprocService, checkRunnerDeps)
    }
}

final case class Runtime(id: Long,
                         googleProject: GoogleProject,
                         runtimeName: String,
                         cloudService: CloudService,
                         status: String) {
  // this is the format we'll output in report, which can be easily consumed by scripts if necessary
  override def toString: String = s"$id,${googleProject.value},${runtimeName},${cloudService},$status"
}
final case class RuntimeCheckerDeps[F[_]](computeService: GoogleComputeService[F],
                                          dataprocService: GoogleDataprocService[F],
                                          checkRunnerDeps: CheckRunnerDeps[F])

final case class KubernetesClusterCheckerDeps[F[_]](checkRunnerDeps: CheckRunnerDeps[F], gkeService: GKEService[F])

final case class DiskCheckerDeps[F[_]](checkRunnerDeps: CheckRunnerDeps[F], googleDiskService: GoogleDiskService[F])

final case class RuntimeCheckerConfig(pathToCredential: Path, reportDestinationBucket: GcsBucketName)

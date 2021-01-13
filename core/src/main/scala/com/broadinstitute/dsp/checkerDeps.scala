package com.broadinstitute.dsp

import java.nio.file.Path

import cats.Parallel
import cats.effect.concurrent.Semaphore
import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Timer}
import io.chrisdavenport.log4cats.StructuredLogger
import org.broadinstitute.dsde.workbench.google2.util.RetryPredicates
import org.broadinstitute.dsde.workbench.google2.{
  GKEService,
  GoogleBillingService,
  GoogleComputeService,
  GoogleDataprocService,
  GoogleDiskService,
  GooglePublisher,
  GoogleStorageService
}
import org.broadinstitute.dsde.workbench.model.google.{GcsBucketName, GoogleProject}
import org.broadinstitute.dsde.workbench.openTelemetry.OpenTelemetryMetrics

object RuntimeCheckerDeps {
  def init[F[_]: Concurrent: ContextShift: StructuredLogger: Parallel: Timer](
    config: RuntimeCheckerConfig,
    blocker: Blocker,
    metrics: OpenTelemetryMetrics[F],
    blockerBound: Semaphore[F]
  ): Resource[F, RuntimeCheckerDeps[F]] =
    for {
      scopedCredential <- initGoogleCredentials(config.pathToCredential)
      computeService <- GoogleComputeService.fromCredential(scopedCredential,
                                                            blocker,
                                                            blockerBound,
                                                            RetryPredicates.standardRetryConfig)
      storageService <- GoogleStorageService.resource(config.pathToCredential.toString,
                                                      blocker,
                                                      Some(blockerBound),
                                                      None)
      dataprocService <- GoogleDataprocService.fromCredential(computeService,
                                                              scopedCredential,
                                                              blocker,
                                                              regionName,
                                                              blockerBound)
    } yield {
      val checkRunnerDeps = CheckRunnerDeps(config.reportDestinationBucket, storageService, metrics)
      RuntimeCheckerDeps(computeService, dataprocService, checkRunnerDeps)
    }
}

final case class Runtime(id: Long,
                         googleProject: GoogleProject,
                         runtimeName: String,
                         cloudService: CloudService,
                         status: String) {
  // this is the format we'll output in report, which can be easily consumed by scripts if necessary
  override def toString: String = s"$id,${googleProject.value},$runtimeName,$cloudService,$status"
}

final case class WorkerCount(num: Int) extends AnyVal
final case class WorkerConfig(numberOfWorkers: Option[Int], numberOfPreemptibleWorkers: Option[Int])
final case class RuntimeWithWorkers(r: Runtime, workerConfig: WorkerConfig) {
  override def toString: String =
    s"Runtime details: ${r.toString}. Worker details: primary: ${workerConfig.numberOfWorkers.getOrElse(0)}, secondary: ${workerConfig.numberOfPreemptibleWorkers
      .getOrElse(0)}"
}
final case class RuntimeCheckerDeps[F[_]](computeService: GoogleComputeService[F],
                                          dataprocService: GoogleDataprocService[F],
                                          checkRunnerDeps: CheckRunnerDeps[F])

final case class KubernetesClusterCheckerDeps[F[_]](checkRunnerDeps: CheckRunnerDeps[F], gkeService: GKEService[F])

final case class NodepoolCheckerDeps[F[_]](checkRunnerDeps: CheckRunnerDeps[F],
                                           gkeService: GKEService[F],
                                           publisher: GooglePublisher[F])

final case class DiskCheckerDeps[F[_]](checkRunnerDeps: CheckRunnerDeps[F], googleDiskService: GoogleDiskService[F])

final case class RuntimeCheckerConfig(pathToCredential: Path, reportDestinationBucket: GcsBucketName)

final case class BillingDeps[F[_]](runtimeCheckerDeps: RuntimeCheckerDeps[F], billingService: GoogleBillingService[F])

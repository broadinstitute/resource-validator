package com.broadinstitute.dsp.resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.mtl.Ask
import cats.implicits._
import com.broadinstitute.dsp.{
  regionName,
  resourceValidator,
  CheckRunner,
  CheckRunnerConfigs,
  CheckRunnerDeps,
  RuntimeCheckerDeps,
  RuntimeWithWorkers
}
import io.chrisdavenport.log4cats.Logger
import org.broadinstitute.dsde.workbench.google2.DataprocClusterName
import org.broadinstitute.dsde.workbench.model.TraceId

object DataprocWorkerChecker {
  val unfixableAnomalyCheckType = "unfixable-dataproc-workers"
  def impl[F[_]: Timer](
    dbReader: DbReader[F],
    deps: RuntimeCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: Ask[F, TraceId]): CheckRunner[F, RuntimeWithWorkers] =
    new CheckRunner[F, RuntimeWithWorkers] {
      override def appName: String = resourceValidator.appName

      override def configs = CheckRunnerConfigs(s"number-of-dataproc-workers", shouldAlert = true)

      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps

      override def resourceToScan: fs2.Stream[F, RuntimeWithWorkers] = dbReader.getRuntimesWithWorkers

      override def checkResource(runtime: RuntimeWithWorkers, isDryRun: Boolean)(
        implicit ev: Ask[F, TraceId]
      ): F[Option[RuntimeWithWorkers]] =
        for {
          clusterOpt <- deps.dataprocService
            .getCluster(runtime.r.googleProject, regionName, DataprocClusterName(runtime.r.runtimeName))
          runtime <- clusterOpt.fold[F[Option[RuntimeWithWorkers]]](F.pure(None)) { c =>
            val doesPrimaryWorkerMatch =
              runtime.workerConfig.numberOfWorkers == c.getConfig.getWorkerConfig.getNumInstances
            val doesSecondaryWorkerMatch =
              runtime.workerConfig.numberOfPreemptibleWorkers == c.getConfig.getSecondaryWorkerConfig.getNumInstances
            val isAnomalyDetected = !(doesPrimaryWorkerMatch && doesSecondaryWorkerMatch)

            isAnomalyDetected match {
              case true =>
                if (isDryRun)
                  logger
                    .warn(
                      s"${runtime} has an anomaly with the number of workers in google. \n\tPrimary work match status: $doesPrimaryWorkerMatch\n\tSecondary worker match status: ${doesSecondaryWorkerMatch}"
                    )
                    .as[Option[RuntimeWithWorkers]](Some(runtime))
                // If the number of primary workers is less than 2, modifying workers requires a creation and deletion. Leo does not handles these, so we will not either
                else if (c.getConfig.getWorkerConfig.getNumInstances >= 2)
                  deps.dataprocService
                    .resizeCluster(
                      runtime.r.googleProject,
                      regionName,
                      DataprocClusterName(runtime.r.runtimeName),
                      if (doesPrimaryWorkerMatch) None else Some(runtime.workerConfig.numberOfWorkers),
                      if (doesSecondaryWorkerMatch) None else Some(runtime.workerConfig.numberOfPreemptibleWorkers)
                    ) >>
                    logger
                      .warn(
                        s"${runtime} has an anomaly with the number of workers in google. \n\tPrimary work match status: $doesPrimaryWorkerMatch\n\tSecondary worker match status: ${doesSecondaryWorkerMatch}"
                      )
                      .as[Option[RuntimeWithWorkers]](Some(runtime))
                else
                  // Here we log a metric if we detect the aforementioned unfixable anomaly. This metric is in addition to the one already reported for finding a worker mismatch
                  deps.checkRunnerDeps.metrics.incrementCounter(s"$appName/$unfixableAnomalyCheckType") >>
                    logger
                      .warn(
                        s"${runtime} has an anomaly with the number of workers in google. Unable to fix the anomaly. Recording a metric and moving on"
                      )
                      .as[Option[RuntimeWithWorkers]](
                        Some(runtime)
                      )
              case false => F.pure(None)
            }
          }
        } yield runtime

    }
}

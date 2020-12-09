//package com.broadinstitute.dsp
//package resourceValidator
//
//import cats.effect.{Concurrent, Timer}
//import cats.implicits._
//import cats.mtl.Ask
//import io.chrisdavenport.log4cats.Logger
//import org.broadinstitute.dsde.workbench.google2.DataprocClusterName
//import org.broadinstitute.dsde.workbench.model.TraceId
//
//// Implements CheckRunner[F[_], A]
//object StoppedDataprocRuntimeChecker {
//  def iml[F[_]: Timer](
//    dbReader: DbReader[F],
//    deps: RuntimeCheckerDeps[F]
//  )(implicit F: Concurrent[F], logger: Logger[F], ev: Ask[F, TraceId]): CheckRunner[F, Runtime] =
//    new CheckRunner[F, Runtime] {
//      override def appName: String = resourceValidator.appName
//      override def configs = CheckRunnerConfigs(s"stopped-dataproc-runtime", shouldAlert = true)
//      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps
//      override def resourceToScan: fs2.Stream[F, Runtime] = dbReader.getStoppedDataprocRuntimes
//
//      override def checkResource(runtime: Runtime, isDryRun: Boolean)(
//        implicit ev: Ask[F, TraceId]
//      ): F[Option[Runtime]] =
//        for {
//          clusterOpt <- deps.dataprocService
//            .getCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
//          _ <- clusterOpt.traverse_ { cluster =>
//            if (cluster.getStatus.getState.name() == "RUNNING")
//              if (isDryRun)
//                logger.warn(s"${runtime} is running. It needs to be stopped.")
//              else
//                // TODO: First remove all its preemptible instances, if any
//                logger.warn(s"${runtime} is running. Going to stop it.") >> deps.dataprocService
//                // In contrast to in Leo, we're not setting the shutdown script metadata before stopping the instance
//                // in order to keep things simple for the time being
//                  .stopCluster(runtime.googleProject,
//                               regionName,
//                               DataprocClusterName(runtime.runtimeName),
//                               metadata = None)
//                  .void
//            else F.unit
//          }
//        } yield clusterOpt.fold(none[Runtime])(_ => Some(runtime))
//    }
//}

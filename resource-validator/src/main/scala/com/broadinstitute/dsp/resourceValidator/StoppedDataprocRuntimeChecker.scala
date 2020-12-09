package com.broadinstitute.dsp
package resourceValidator

import cats.effect.{Concurrent, Timer}
import cats.implicits._
import cats.mtl.Ask
//import com.broadinstitute.dsp.CloudService.{Dataproc, Gce}
//import com.google.api.services.dataproc.Dataproc
//import com.google.api.services.dataproc.model.{
//  Cluster => DataprocCluster,
//  ClusterConfig => DataprocClusterConfig,
//  Operation => DataprocOperation,
//  ClusterStatus => _
//  _
//}
import io.chrisdavenport.log4cats.Logger
//import org.broadinstitute.dsde.workbench.google.GoogleCredentialModes._
import org.broadinstitute.dsde.workbench.google2.{DataprocClusterName, InstanceName}
import org.broadinstitute.dsde.workbench.model.TraceId
//import org.broadinstitute.dsde.workbench.model.google.GoogleProject
//import DataprocModels._
//
//import scala.concurrent.Future

// Implements CheckRunner[F[_], A]
object StoppedDataprocRuntimeChecker {
//  private lazy val dataproc = {
//    new com.google.api.services.dataproc.Dataproc.Builder(httpTransport, jsonFactory, googleCredential)
//      .setApplicationName(appName)
//      .build()
//  }

  def iml[F[_]: Timer](
    dbReader: DbReader[F],
    deps: RuntimeCheckerDeps[F]
  )(implicit F: Concurrent[F], logger: Logger[F], ev: Ask[F, TraceId]): CheckRunner[F, Runtime] =
    new CheckRunner[F, Runtime] {
      override def appName: String = resourceValidator.appName
      override def configs = CheckRunnerConfigs(s"stopped-dataproc-runtime", shouldAlert = true)
      override def dependencies: CheckRunnerDeps[F] = deps.checkRunnerDeps
      override def resourceToScan: fs2.Stream[F, Runtime] = dbReader.getStoppedDataprocRuntimes

      override def checkResource(runtime: Runtime, isDryRun: Boolean)(
        implicit ev: Ask[F, TraceId]
      ): F[Option[Runtime]] =
        for {
          clusterOpt <- deps.dataprocService
            .getCluster(runtime.googleProject, regionName, DataprocClusterName(runtime.runtimeName))
          _ <- clusterOpt.traverse_ { cluster =>
            if (cluster.getStatus.getState.name() == "RUNNING")
              if (isDryRun)
                logger
                  .warn(s"${runtime} is running. It needs to be stopped.")
              else
                // TODO: First remove all its preemptible instances, if any
                logger.warn(s"${runtime} is running. Going to stop it.") >> deps.computeService
                // In contrast to in Leo, we're not setting the shutdown script metadata before stopping the instance
                // in order to keep things simple for the time being
                  .stopInstance(runtime.googleProject, zoneName, InstanceName(runtime.runtimeName))
                  .void
            else F.unit
          }
        } yield clusterOpt.fold(none[Runtime])(_ => Some(runtime))

//      def resizeCluster(googleProject: GoogleProject,
//                        clusterName: RuntimeName,
//                        numWorkers: Option[Int] = None,
//                        numPreemptibles: Option[Int] = None): Future[Unit] = {
//        val workerMask = "config.worker_config.num_instances"
//        val preemptibleMask = "config.secondary_worker_config.num_instances"
//
//        val updateAndMask = (numWorkers, numPreemptibles) match {
//          case (Some(nw), Some(np)) =>
//            val mask = List(Some(workerMask), Some(preemptibleMask)).flatten.mkString(",")
//            val update = new DataprocCluster().setConfig(
//              new DataprocClusterConfig()
//                .setWorkerConfig(new InstanceGroupConfig().setNumInstances(nw))
//                .setSecondaryWorkerConfig(new InstanceGroupConfig().setNumInstances(np))
//            )
//            Some((update, mask))
//
//          case (Some(nw), None) =>
//            val mask = workerMask
//            val update = new DataprocCluster().setConfig(
//              new DataprocClusterConfig()
//                .setWorkerConfig(new InstanceGroupConfig().setNumInstances(nw))
//            )
//            Some((update, mask))
//
//          case (None, Some(np)) =>
//            val mask = preemptibleMask
//            val update = new DataprocCluster().setConfig(
//              new DataprocClusterConfig()
//                .setSecondaryWorkerConfig(new InstanceGroupConfig().setNumInstances(np))
//            )
//            Some((update, mask))
//
//          case (None, None) =>
//            None
//        }
//
//        updateAndMask match {
//          case Some((update, mask)) =>
//            val request = dataproc
//              .projects()
//              .regions()
//              .clusters()
//              .patch(googleProject.value, regionName.value, clusterName.asString, update)
//              .setUpdateMask(mask)
//            retry(retryPredicates: _*)(() => executeGoogleRequest(request)).void
//          case None => Future.successful(())
//        }
//      }
    }
}

object DataprocModels {
  final case class DataprocClusterName(asString: String) extends AnyVal

//  final case class DataprocInstance(clusterId: Long,
//                                    googleProject: GoogleProject,
//                                    runtimeName: String,
//                                    status: String) {
//    // this is the format we'll output in report, which can be easily consumed by scripts if necessary
//    override def toString: String = s"$id,${googleProject.value},${runtimeName},${cloudService},$status"
//  }
}

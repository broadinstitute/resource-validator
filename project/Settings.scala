import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import sbt._

object Settings {
  lazy val dockerSettings = List(
    maintainer := "workbench-interactive-analysis@broadinstitute.org",
    dockerBaseImage := "oracle/graalvm-ce:20.2.0-java8",
    dockerRepository := Some("us.gcr.io"),
    dockerUpdateLatest := true,
    mainClass in Compile := Some("com.broadinstitute.dsp.resourcevalidator.Main"),
    packageName in Docker := "broad-dsp-gcr-public/resource-validator",
    dockerAlias := DockerAlias(
      Some("us.gcr.io"),
      None,
      "broad-dsp-gcr-public/resource-validator",
      Some("0.0.1-rc4")
    )
  )
}

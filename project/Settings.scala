import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.Keys.scriptClasspath
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly.AssemblyPlugin.autoImport.assembly

object Settings {
  private lazy val artifactory = "https://artifactory.broadinstitute.org/artifactory/"

  private lazy val commonResolvers = List(
    "artifactory-releases" at artifactory + "libs-release",
    "artifactory-snapshots" at artifactory + "libs-snapshot"
  )

  private lazy val resourceValidatorDockerSettings = List(
    dockerUpdateLatest := true,
    Compile / mainClass := Some("com.broadinstitute.dsp.resourceValidator.Main"),
    Docker / packageName := "broad-dsp-gcr-public/resource-validator",
    dockerAlias := DockerAlias(
      Some("us.gcr.io"),
      None,
      "broad-dsp-gcr-public/resource-validator",
      None
    )
  )

  private lazy val zombieMonitorDockerSettings = List(
    dockerUpdateLatest := true,
    Compile / mainClass := Some("com.broadinstitute.dsp.zombieMonitor.Main"),
    Docker / packageName := "broad-dsp-gcr-public/zombie-monitor",
    dockerAlias := DockerAlias(
      Some("us.gcr.io"),
      None,
      "broad-dsp-gcr-public/zombie-monitor",
      None
    )
  )

  private lazy val cleanupDockerSettings = List(
    dockerUpdateLatest := true,
    Compile / mainClass := Some("com.broadinstitute.dsp.cleanup.Main"),
    Docker / packageName := "broad-dsp-gcr-public/cleanup",
    dockerAlias := DockerAlias(
      Some("us.gcr.io"),
      None,
      "broad-dsp-gcr-public/cleanup",
      None
    )
  )

  private val commonCompilerSettings = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-language:higherKinds",
    "-language:postfixOps",
    "-feature",
    "-Xfatal-warnings",
    "-Ywarn-unused:imports",
    "-Ymacro-annotations"
  )

  private lazy val commonSettings = List(
    organization := "com.broadinstitute.dsp",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.6",
    resolvers ++= commonResolvers,
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    // Docker settings
    maintainer := "workbench-interactive-analysis@broadinstitute.org",
    dockerBaseImage := "ghcr.io/graalvm/graalvm-ce:java11-21.0.0.2",
    dockerRepository := Some("us.gcr.io"),
    scalacOptions ++= commonCompilerSettings,
    // assembly merge
    assembly / assemblyMergeStrategy := Merging.customMergeStrategy((assembly / assemblyMergeStrategy).value),
    assembly / test := {}
  )

  lazy val coreSettings = commonSettings ++ List(
    libraryDependencies ++= Dependencies.core
  )

  lazy val resourceValidatorSettings = commonSettings ++ resourceValidatorDockerSettings ++ List(
    name := "resource-validator",
    libraryDependencies ++= Dependencies.resourceValidator,
    assembly / assemblyJarName := "resource-validator-assembly.jar",
    // removes all jar mappings in universal and appends the fat jar
    // This is needed to include `core` module in classpath
    Universal / mappings := {
      // universalMappings: Seq[(File,String)]
      val universalMappings = (Universal / mappings).value
      val fatJar = (Compile / assembly).value
      // removing means filtering
      val filtered = universalMappings filter {
        case (_, name) => !name.endsWith(".jar")
      }
      // add the fat jar
      filtered :+ (fatJar -> ("lib/" + fatJar.getName))
    },
    scriptClasspath := Seq((assembly / assemblyJarName).value)
  )

  lazy val zombieMonitorSettings = commonSettings ++ zombieMonitorDockerSettings ++ List(
    name := "zombie-monitor",
    libraryDependencies ++= Dependencies.zombieMonitor,
    assembly / assemblyJarName := "zombie-monitor-assembly.jar",
    // removes all jar mappings in universal and appends the fat jar
    // This is needed to include `core` module in classpath
    Universal / mappings := {
      // universalMappings: Seq[(File,String)]
      val universalMappings = (Universal / mappings).value
      val fatJar = (Compile / assembly).value
      // removing means filtering
      val filtered = universalMappings filter {
        case (_, name) => !name.endsWith(".jar")
      }
      // add the fat jar
      filtered :+ (fatJar -> ("lib/" + fatJar.getName))
    },
    scriptClasspath := Seq((assembly / assemblyJarName).value)
  )

  lazy val nukerSettings = commonSettings ++ cleanupDockerSettings ++ List(
    name := "nuker",
    libraryDependencies ++= Dependencies.cleanup,
    assembly / assemblyJarName := "nuker-assembly.jar",
    // removes all jar mappings in universal and appends the fat jar
    // This is needed to include `core` module in classpath
    Universal / mappings := {
      // universalMappings: Seq[(File,String)]
      val universalMappings = (Universal / mappings).value
      val fatJar = (Compile / assembly).value
      // removing means filtering
      val filtered = universalMappings filter {
        case (_, name) => !name.endsWith(".jar")
      }
      // add the fat jar
      filtered :+ (fatJar -> ("lib/" + fatJar.getName))
    },
    scriptClasspath := Seq((assembly / assemblyJarName).value)
  )
}

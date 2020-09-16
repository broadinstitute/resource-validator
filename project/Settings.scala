import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.Keys.scriptClasspath
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys._
import sbtassembly.AssemblyPlugin.autoImport.assembly

object Settings {
  lazy val artifactory = "https://artifactory.broadinstitute.org/artifactory/"

  lazy val commonResolvers = List(
    "artifactory-releases" at artifactory + "libs-release",
    "artifactory-snapshots" at artifactory + "libs-snapshot"
  )

  lazy val resourceValidatorDockerSettings = List(
    dockerUpdateLatest := true,
    mainClass in Compile := Some("com.broadinstitute.dsp.resourceValidator.Main"),
    packageName in Docker := "broad-dsp-gcr-public/resource-validator",
    dockerAlias := DockerAlias(
      Some("us.gcr.io"),
      None,
      "broad-dsp-gcr-public/resource-validator",
      None
    )
  )

  lazy val zombieMonitorDockerSettings = List(
    dockerUpdateLatest := true,
    mainClass in Compile := Some("com.broadinstitute.dsp.zombieMonitor.Main"),
    packageName in Docker := "broad-dsp-gcr-public/zombie-monitor",
    dockerAlias := DockerAlias(
      Some("us.gcr.io"),
      None,
      "broad-dsp-gcr-public/zombie-monitor",
      None
    )
  )

  val commonCompilerSettings = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-language:higherKinds",
    "-language:postfixOps",
    "-feature",
    "-Xfatal-warnings"
  )

  lazy val commonSettings = List(
    organization := "com.broadinstitute.dsp",
    name := "resource-validator",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.3",
    resolvers ++= commonResolvers,
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    // Docker settings
    maintainer := "workbench-interactive-analysis@broadinstitute.org",
    dockerBaseImage := "oracle/graalvm-ce:20.2.0-java8",
    dockerRepository := Some("us.gcr.io"),
    scalacOptions ++= commonCompilerSettings,
    // assembly merge
    assemblyMergeStrategy in assembly := Merging.customMergeStrategy((assemblyMergeStrategy in assembly).value),
    test in assembly := {}
  )

  lazy val coreSettings = commonSettings ++ List(
    libraryDependencies ++= Dependencies.core
  )

  lazy val resourceValidatorSettings = commonSettings ++ resourceValidatorDockerSettings ++ List(
    libraryDependencies ++= Dependencies.resourceValidator,
    assemblyJarName in assembly := "resource-validator-assembly.jar",
    // removes all jar mappings in universal and appends the fat jar
    // This is needed to include `core` module in classpath
    mappings in Universal := {
      // universalMappings: Seq[(File,String)]
      val universalMappings = (mappings in Universal).value
      val fatJar = (assembly in Compile).value
      // removing means filtering
      val filtered = universalMappings filter {
        case (_, name) => !name.endsWith(".jar")
      }
      // add the fat jar
      filtered :+ (fatJar -> ("lib/" + fatJar.getName))
    },
    scriptClasspath := Seq((assemblyJarName in assembly).value)
  )

  lazy val zombieMonitorSettings = commonSettings ++ zombieMonitorDockerSettings ++ List(
    libraryDependencies ++= Dependencies.zombieMonitor,
    assemblyJarName in assembly := "zombie-monitor-assembly.jar",
    // removes all jar mappings in universal and appends the fat jar
    // This is needed to include `core` module in classpath
    mappings in Universal := {
      // universalMappings: Seq[(File,String)]
      val universalMappings = (mappings in Universal).value
      val fatJar = (assembly in Compile).value
      // removing means filtering
      val filtered = universalMappings filter {
        case (_, name) => !name.endsWith(".jar")
      }
      // add the fat jar
      filtered :+ (fatJar -> ("lib/" + fatJar.getName))
    },
    scriptClasspath := Seq((assemblyJarName in assembly).value)
  )
}

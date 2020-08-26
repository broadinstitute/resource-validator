val LogbackVersion = "1.2.3"
val workbenchUtil2 = "0.1-92fcd96"
val doobieVersion = "0.9.0"

lazy val artifactory = "https://artifactory.broadinstitute.org/artifactory/"

lazy val commonResolvers = List(
  "artifactory-releases" at artifactory + "libs-release",
  "artifactory-snapshots" at artifactory + "libs-snapshot"
)

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

lazy val root = (project in file("."))
  .settings(
    organization := "com.broadinstitute.dsp",
    name := "resource-validator",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.3",
    Settings.dockerSettings,
    resolvers ++= commonResolvers,
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "com.google.cloud" % "google-cloud-dataproc" % "0.122.1",
      "com.google.cloud" % "google-cloud-compute" % "0.118.0-alpha",
      "co.fs2" %% "fs2-io" % "2.4.2",
      "org.broadinstitute.dsde.workbench" %% "workbench-util2" % workbenchUtil2,
      "com.github.pureconfig" %% "pureconfig" % "0.13.0",
      "com.monovore" %% "decline" % "1.0.0",
      "mysql" % "mysql-connector-java" % "8.0.18",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Xfatal-warnings"
)

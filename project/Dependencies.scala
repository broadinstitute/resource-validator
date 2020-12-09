import sbt._

object Dependencies {
  val logbackVersion = "1.2.3"
  val workbenchGoogle2Version = "0.18-c9edd8e"
  val doobieVersion = "0.9.4"
  val openTelemetryVersion = "0.1-e66171c"

  val core = Seq(
    "net.logstash.logback" % "logstash-logback-encoder" % "6.2",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "ch.qos.logback" % "logback-core" % "1.2.3",
    "org.tpolecat" %% "doobie-core" % doobieVersion,
    "org.tpolecat" %% "doobie-hikari" % doobieVersion,
    "org.tpolecat" %% "doobie-scalatest" % doobieVersion % Test,
    "com.github.pureconfig" %% "pureconfig" % "0.13.0",
    "mysql" % "mysql-connector-java" % "8.0.18",
    "org.scalatest" %% "scalatest" % "3.2.0" % Test,
    "com.monovore" %% "decline" % "1.0.0",
    "co.fs2" %% "fs2-io" % "2.4.2",
    "org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % openTelemetryVersion,
    "org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % openTelemetryVersion % Test classifier "tests",
    "com.google.apis" % "google-api-services-dataproc" % "v1-rev91-1.23.0",
    "com.google.cloud" % "google-cloud-dataproc" % "0.122.1",
    "com.google.cloud" % "google-cloud-compute" % "0.118.0-alpha",
    "org.broadinstitute.dsde.workbench" %% "workbench-google2" % workbenchGoogle2Version,
    "org.broadinstitute.dsde.workbench" %% "workbench-google2" % workbenchGoogle2Version % Test classifier "tests",
    "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % Test,
    "org.scalatestplus" %% "mockito-3-3" % "3.2.0.0" % Test // https://github.com/scalatest/scalatestplus-selenium
  )

  val resourceValidator = core

  val zombieMonitor = core

  val cleanup = core
}

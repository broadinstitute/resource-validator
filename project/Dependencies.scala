import sbt._

object Dependencies {
  val logbackVersion = "1.2.3"
  val workbenchGoogle2Version = "0.20-52e271f"
  val doobieVersion = "0.13.4"
  val openTelemetryVersion = "0.1-52e271f"

  val core = Seq(
    "net.logstash.logback" % "logstash-logback-encoder" % "6.6",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "ch.qos.logback" % "logback-core" % "1.2.3",
    "org.tpolecat" %% "doobie-core" % doobieVersion,
    "org.tpolecat" %% "doobie-hikari" % doobieVersion,
    "org.tpolecat" %% "doobie-scalatest" % doobieVersion % Test,
    "com.github.pureconfig" %% "pureconfig" % "0.16.0",
    "mysql" % "mysql-connector-java" % "8.0.23",
    "org.scalatest" %% "scalatest" % "3.2.7" % Test,
    "com.monovore" %% "decline" % "2.0.0",
    "com.github.julien-truffaut" %% "monocle-core" % "3.0.0-M6",
    "com.github.julien-truffaut" %% "monocle-macro" % "3.0.0-M6",
    "org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % openTelemetryVersion,
    "org.broadinstitute.dsde.workbench" %% "workbench-opentelemetry" % openTelemetryVersion % Test classifier "tests",
    "org.broadinstitute.dsde.workbench" %% "workbench-google2" % workbenchGoogle2Version,
    "org.broadinstitute.dsde.workbench" %% "workbench-google2" % workbenchGoogle2Version,
    "org.broadinstitute.dsde.workbench" %% "workbench-google2" % workbenchGoogle2Version % Test classifier "tests",
    "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % Test,
    "org.scalatestplus" %% "mockito-3-4" % "3.2.9.0" % Test, // https://github.com/scalatest/scalatestplus-selenium
    "ca.mrvisser" %% "sealerate" % "0.0.6"
  )

  val resourceValidator = core

  val zombieMonitor = core

  val janitor = core

  val nuker = core
}

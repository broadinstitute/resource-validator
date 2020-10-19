import sbt._

object Dependencies {
  val LogbackVersion = "1.2.3"
  val workbenchGoogle2 = "0.13-39c1b35"
  val doobieVersion = "0.9.0"

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
    "com.google.cloud" % "google-cloud-dataproc" % "0.122.1",
    "com.google.cloud" % "google-cloud-compute" % "0.118.0-alpha",
    "org.broadinstitute.dsde.workbench" %% "workbench-google2" % workbenchGoogle2,
    "org.broadinstitute.dsde.workbench" %% "workbench-google2" % workbenchGoogle2 % Test classifier "tests",
    "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % Test,
    "org.scalatestplus" %% "mockito-3-3" % "3.2.0.0" % Test // https://github.com/scalatest/scalatestplus-selenium
  )

  val resourceValidator = core

  val zombieMonitor = core
}

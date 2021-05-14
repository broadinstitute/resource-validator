lazy val root = project
  .in(file("."))
  .settings(
    name := "leonardo-cron-jobs",
    publish / skip := true
  )
  .aggregate(core, resourceValidator, zombieMonitor)

lazy val core = (project in file("core"))
  .settings(
    Settings.coreSettings
  )

lazy val resourceValidator = (project in file("resource-validator"))
  .settings(Settings.resourceValidatorSettings)
  .enablePlugins(JavaAppPackaging)
  .dependsOn(core % "test->test;compile->compile")

lazy val zombieMonitor = (project in file("zombie-monitor"))
  .settings(Settings.zombieMonitorSettings)
  .enablePlugins(JavaAppPackaging)
  .dependsOn(core % "test->test;compile->compile")

lazy val janitor = (project in file("janitor"))
  .settings(Settings.janitorSettings)
  .enablePlugins(JavaAppPackaging)
  .dependsOn(core % "test->test;compile->compile")

lazy val cleanup = (project in file("nuker"))
  .settings(Settings.nukerSettings)
  .enablePlugins(JavaAppPackaging)
  .dependsOn(core % "test->test;compile->compile")

Test / parallelExecution := false

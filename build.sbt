lazy val root = project
  .in(file("."))
  .settings(
    name := "leonardo-cron-jobs",
    skip in publish := true
  )
  .aggregate(core, resourceValidator, zombieMonitor)

lazy val core = (project in file("core"))
  .settings(
    Settings.coreSettings
  )

//lazy val resourceValidatorDockerSettings = List(
//  docker := (docker dependsOn (AssemblyKeys.assembly in core)),
//  dockerfile in docker := {
//    val artifact: File = (AssemblyKeys.assemblyOutputPath in AssemblyKeys.assembly in core).value
//
//    val artifactTargetPath = s"/app/${artifact.name}"
//
//    new Dockerfile {
//      from("oracle/graalvm-ce:20.2.0-java8")
//      add(artifact, artifactTargetPath)
//      entryPoint("java", "-jar", artifactTargetPath)
//    }
//  },
//  imageNames in docker := Seq(
//    // Sets the latest tag
//    ImageName(s"${organization.value}/${name.value}:latest"),
//    // Sets a name with a tag that contains the project version
//    ImageName(
//      repository = name.value,
//      tag = Some("v" + version.value)
//    )
//  )
//)

lazy val resourceValidator = (project in file("resource-validator"))
  .settings(Settings.resourceValidatorSettings)
  //  .settings(resourceValidatorDockerSettings)
  .enablePlugins(JavaAppPackaging)
  .dependsOn(core % "test->test;compile->compile")

lazy val zombieMonitor = (project in file("zombie-monitor"))
  .settings(Settings.zombieMonitorSettings)
  .enablePlugins(JavaAppPackaging)
  .dependsOn(core % "test->test;compile->compile")

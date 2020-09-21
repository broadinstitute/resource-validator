package com.broadinstitute.dsp

import java.nio.file.Paths

import org.broadinstitute.dsde.workbench.model.google.GcsBucketName
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigSpec extends AnyFlatSpec with Matchers {
  it should "read config file correctly" in {
    val config = Config.appConfig
    val expectedConfig = AppConfig(
      DatabaseConfig(
        "jdbc:mysql://localhost:3311/leonardo?rewriteBatchedStatements=true&nullNamePatternMatchesAll=true",
        "leonardo-test",
        "leonardo-test"
      ),
      Paths.get("path-to-credential"),
      GcsBucketName("fake-bucket")
    )

    config shouldBe Right(expectedConfig)
  }
}

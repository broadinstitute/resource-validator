package com.broadinstitute.dsp.resourcevalidator

import java.nio.file.Paths

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConfigSpec extends AnyFlatSpec with Matchers {
  it should "read config file correctly" in {
    val config = Config.appConfig
    val expectedConfig = AppConfig(
      DatabaseConfig(
        "jdbc:mysql://127.0.0.1:3306/leonardo?rewriteBatchedStatements=true&nullNamePatternMatchesAll=true",
        "username",
        "password"
      ),
      Paths.get("path-to-credential")
    )

    config shouldBe Right(expectedConfig)
  }
}

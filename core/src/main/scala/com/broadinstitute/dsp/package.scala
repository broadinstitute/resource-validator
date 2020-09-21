package com.broadinstitute

import cats.effect.{Resource, Sync, Timer}
import com.google.auth.oauth2.{GoogleCredentials, ServiceAccountCredentials}
import org.broadinstitute.dsde.workbench.google2.{RegionName, ZoneName}

import scala.jdk.CollectionConverters._

package object dsp {
  val zoneName = ZoneName("us-central1-a")
  val regionName = RegionName("us-central1")

  def initGoogleCredentials[F[_]: Sync: Timer](
    appConfig: AppConfig
  ): Resource[F, GoogleCredentials] =
    for {
      credentialFile <- org.broadinstitute.dsde.workbench.util2.readFile[F](appConfig.pathToCredential.toString)
      credential <- Resource.liftF(Sync[F].delay(ServiceAccountCredentials.fromStream(credentialFile)))
    } yield credential.createScoped(Seq("https://www.googleapis.com/auth/cloud-platform").asJava)
}

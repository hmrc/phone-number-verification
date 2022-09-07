import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  val hmrcBootstrapVersion = "7.2.0"
  val hmrcMongoPlayVersion = "0.71.0"
  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-28"  % hmrcBootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % hmrcMongoPlayVersion,
    "io.jsonwebtoken"         %  "jjwt-api"                   % "0.10.8"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % hmrcBootstrapVersion  % "test, it",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % hmrcMongoPlayVersion  % "test, it",
    "org.mockito"             %% "mockito-scala"              % "1.17.7"              % Test
  )
}

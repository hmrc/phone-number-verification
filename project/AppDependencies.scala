import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  val hmrcBootstrapVersion = "7.2.0"
  val hmrcMongoPlayVersion = "0.71.0"

  val compile = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-28"    % hmrcBootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"           % hmrcMongoPlayVersion,
    "uk.gov.hmrc"                  %% "internal-auth-client-play-28" % "1.2.0",
    "io.jsonwebtoken"               % "jjwt-api"                     % "0.11.5",
    "com.googlecode.libphonenumber" % "libphonenumber"               % "8.13.12"
  )

  val test = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-28"  % hmrcBootstrapVersion % "test, it",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % hmrcMongoPlayVersion % "test, it",
    "org.mockito"       %% "mockito-scala"           % "1.17.12"            % "test, it"
  )
}

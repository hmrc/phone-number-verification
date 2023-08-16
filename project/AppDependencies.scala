import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  val hmrcBootstrapVersion = "7.21.0"
  val hmrcMongoPlayVersion = "1.3.0"

  val compile = Seq(
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % hmrcBootstrapVersion,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % hmrcMongoPlayVersion,
    "io.jsonwebtoken"               % "jjwt-api"       % "0.11.5",
    "com.googlecode.libphonenumber" % "libphonenumber" % "8.13.12"
  )

  val test = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-28"  % hmrcBootstrapVersion % "test, it",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % hmrcMongoPlayVersion % "test, it",
    "org.mockito"       %% "mockito-scala"           % "1.17.14"            % "test, it"
  )
}

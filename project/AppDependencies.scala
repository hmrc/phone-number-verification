import sbt.*

object AppDependencies {
  private val bootstrapPlayVersion = "8.6.0"
  private val mongoVersion = "1.6.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30"    % bootstrapPlayVersion,
    "uk.gov.hmrc"                  %% "internal-auth-client-play-30" % "1.10.0",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"           % mongoVersion,
    "com.googlecode.libphonenumber" % "libphonenumber"               % "8.13.12"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % mongoVersion         % Test
  )
}

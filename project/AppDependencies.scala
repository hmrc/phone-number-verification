import sbt.*

object AppDependencies {

  private val bootstrapPlayVersion = "9.19.0"
  private val playSuffix           = "-play-30"
  private val mongoVersion         = "2.7.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% s"bootstrap-backend$playSuffix"    % bootstrapPlayVersion,
    "uk.gov.hmrc"                  %% s"internal-auth-client$playSuffix" % "1.10.0",
    "uk.gov.hmrc.mongo"            %% s"hmrc-mongo$playSuffix"           % mongoVersion,
    "com.googlecode.libphonenumber" % "libphonenumber"                   % "9.0.12"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test$playSuffix"  % bootstrapPlayVersion,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test$playSuffix" % mongoVersion
  ).map(_ % Test)
}

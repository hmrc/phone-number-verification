
import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

val appName = "cip-phone-number-verification"

val silencerVersion = "1.7.7"

lazy val scalaCompilerOptions = Seq(
  "-Xlint:-missing-interpolator,_",
  "-Ywarn-unused:imports",
  "-Ywarn-unused:privates",
  "-Ywarn-unused:locals",
  "-Ywarn-unused:explicits",
  "-Ywarn-unused:implicits",
  "-Ywarn-value-discard",
  "-Ywarn-unused:patvars",
  "-Ywarn-dead-code",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:implicitConversions"
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion                     := 0,
    scalaVersion                     := "2.13.7",
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test,
    // ***************
    // Use the silencer plugin to suppress warnings
    scalacOptions ++= scalaCompilerOptions,
    scalacOptions += "-P:silencer:pathFilters=routes",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
    // ***************
  )
  .settings(
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    IntegrationTest / scalafmtOnCompile := true,
  )
  .settings(
    PlayKeys.playDefaultPort := 6083
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)


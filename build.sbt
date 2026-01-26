import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 1
ThisBuild / scalaVersion := "3.3.7"

val appName = "phone-number-verification"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin, BuildInfoPlugin)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalacOptions += "-Wconf:src=routes/.*:s"
  )
  .settings(
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile    := true
  )
  .settings(
    buildInfoKeys    := Seq[BuildInfoKey](version),
    buildInfoPackage := "buildinfo"
  )
  .settings(
    PlayKeys.playDefaultPort := 6083
  )
  .settings(CodeCoverageSettings.settings *)

lazy val it = project
  .in(file("it"))
  .enablePlugins(play.sbt.PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(Test / fork := true)
  .settings(
    Test / scalafmtOnCompile := true
  )

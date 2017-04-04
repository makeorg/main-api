lazy val commonSettings = Seq(
  organization := "org.make",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.1",
  libraryDependencies += Dependencies.scalaTest
)

lazy val elastic = project.in(file("."))
  .settings(commonSettings: _*)
  .settings(
    moduleName := "make-elastic": _*
  )
  .aggregate(core, api)

lazy val core = project.in(file("core"))
  .settings(commonSettings: _*)

lazy val api = project.in(file("api"))
  .settings(commonSettings: _*)
  .dependsOn(core)



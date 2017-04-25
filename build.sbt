import sbt.Keys.scalacOptions

lazy val commonSettings = Seq(
  organization := "org.make",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.12.1",
  libraryDependencies ++= Seq(
    Dependencies.logger,
    Dependencies.loggerBridge,
    Dependencies.scalaLogging,
    Dependencies.scalaTest,
    Dependencies.mockito
  ),
  publishTo := {
    if (isSnapshot.value) {
      Some("Sonatype Snapshots Nexus" at "https://nexus.prod.makeorg.tech/repository/maven-snapshots/")
    } else {
      Some("Sonatype Snapshots Nexus" at "https://nexus.prod.makeorg.tech/repository/maven-releases/")
    }
  },
  resolvers += "Confluent Releases" at "http://packages.confluent.io/maven/",
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    //    "-Xfatal-warnings",
    "-Ywarn-unused",
    "-Ywarn-dead-code",
    "-feature",
    "-language:_"
  )
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



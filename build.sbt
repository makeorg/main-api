import sbt.Keys.scalacOptions
import Tasks.{testScalastyle, _}

lazy val commonSettings = Seq(
  organization := "org.make",
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
      Some("Sonatype Snapshots Nexus".at("https://nexus.prod.makeorg.tech/repository/maven-snapshots/"))
    } else {
      Some("Sonatype Snapshots Nexus".at("https://nexus.prod.makeorg.tech/repository/maven-releases/"))
    }
  },
  resolvers += "Confluent Releases".at("http://packages.confluent.io/maven/"),
  scalastyleFailOnError := true,
  compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value,
  testScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Test).toTask("").value,
  (compile in Compile) := (compile in Compile).dependsOn(compileScalastyle).value,
  (test in Test) := (test in Test).dependsOn(testScalastyle).value,
  scalacOptions ++= Seq(
    "-Yrangepos",
    "-Xlint",
    "-deprecation",
    "-Xfatal-warnings",
    "-feature",
    "-encoding",
    "UTF-8",
    "-unchecked",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Xfuture",
    // "-Ywarn-unused",
    "-Ywarn-unused-import",
    "-Ydelambdafy:method",
    "-language:_"
  )
)

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17")

lazy val elastic = project
  .in(file("."))
  .settings(commonSettings: _*)
  .settings(moduleName := "make-elastic": _*)
  .aggregate(core, api)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings: _*)

lazy val api = project
  .in(file("api"))
  .settings(commonSettings: _*)
  .dependsOn(core)

credentials ++= {
  if (System.getenv().containsKey("CI_BUILD")) {
    Seq(
      Credentials(
        "Some Nexus Repository Manager",
        System.getenv("NEXUS_URL"),
        System.getenv("NEXUS_USER"),
        System.getenv("NEXUS_PASSWORD")
      )
    )
  } else {
    Nil
  }
}

import Tasks._
import org.make.GitHooks
import sbt.Keys.scalacOptions

lazy val commonSettings = Seq(
  organization := "org.make",
  scalaVersion := "2.12.1",
  coursierUseSbtCredentials := true,
  credentials ++= {
    if (System.getenv().containsKey("CI_BUILD")) {
      Seq(
        Credentials(
          "Sonatype Nexus Repository Manager",
          System.getenv("NEXUS_URL"),
          System.getenv("NEXUS_USER"),
          System.getenv("NEXUS_PASSWORD")
        )
      )
    } else {
      Nil
    }
  },
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
      Some("Sonatype Releases Nexus".at("https://nexus.prod.makeorg.tech/repository/maven-releases/"))
    }
  },
  resolvers += "Confluent Releases".at("http://packages.confluent.io/maven/"),
  resolvers += "Sonatype Nexus Repository Manager".at("https://nexus.prod.makeorg.tech/repository/maven-public/"),
  scalastyleFailOnError := true,
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

lazy val phantom = project
  .in(file("."))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .settings(commonSettings: _*)
  .settings(moduleName := "make-phantom": _*)
  .aggregate(core, api)

lazy val fixtures = project
  .in(file("fixtures"))
  .settings(commonSettings: _*)
  .configs(Gatling)
  .settings(moduleName := "make-fixtures": _*)

lazy val core = project
  .in(file("core"))
  .configs(IntegrationTest)
  .settings(commonSettings: _*)
  .settings(Defaults.itSettings: _*)

lazy val api = project
  .in(file("api"))
  .configs(IntegrationTest)
  .settings(commonSettings: _*)
  .settings(Defaults.itSettings: _*)
  .settings(imageName := {
    val alias = dockerAlias.value
    s"${alias.registryHost.map(_ + "/").getOrElse("")}${alias.name}:${alias.tag.getOrElse("latest")}"
  })
  .dependsOn(core)

enablePlugins(GitHooks)

gitCommitMessageHook := Some(baseDirectory.value / "bin" / "commit-msg.hook")

/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

import Tasks._
import org.make.GitHooks
import sbt.Keys.scalacOptions

lazy val commonSettings = Seq(
  organization := "org.make",
  scalaVersion := "2.12.6",
  licenses += "AGPL-3.0-or-later" -> url("https://www.gnu.org/licenses/agpl.html"),
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
    "-Ywarn-unused-import",
    "-Ywarn-unused",
    "-language:_",
    "-Ycache-plugin-class-loader:last-modified",
    "-Ycache-macro-class-loader:last-modified",
    "-Ybackend-parallelism",
    "5"
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
  .settings(
    organization := "org.make",
    scalaVersion := "2.12.4"
  )
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
  }, testOptions in IntegrationTest += Tests.Argument("-oF"))
  .dependsOn(core)

isSnapshot in ThisBuild := false

git.formattedShaVersion := git.gitHeadCommit.value map { sha => sha.take(10) }

version in ThisBuild := {
  git.formattedShaVersion.value.get
}

gitCommitMessageHook := Some(baseDirectory.value / "bin" / "commit-msg.hook")

enablePlugins(GitHooks)
enablePlugins(GitVersioning)
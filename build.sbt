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

import java.time.LocalDate

import Syntax._
import Tasks._
import org.make.GitHooks
import sbt.Keys.scalacOptions
import kamon.instrumentation.sbt.SbtKanelaRunner.Keys.kanelaVersion
import ScalafmtPlugin.scalafmtConfigSettings
import ScalastylePlugin.rawScalastyleSettings
import wartremover.{Wart, Warts}

lazy val commonSettings = Seq(
  organization := "org.make",
  scalaVersion := "2.13.6",
  licenses     += "AGPL-3.0-or-later" -> url("https://www.gnu.org/licenses/agpl.html"),
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
  kanelaVersion       := Dependencies.kanelaVersion,
  libraryDependencies ++= Seq(Dependencies.logger, Dependencies.loggerBridge, Dependencies.grizzledSlf4j),
  publishTo := {
    if (isSnapshot.value) {
      Some("Sonatype Snapshots Nexus".at("https://nexus.prod.makeorg.tech/repository/maven-snapshots/"))
    } else {
      Some("Sonatype Releases Nexus".at("https://nexus.prod.makeorg.tech/repository/maven-releases/"))
    }
  },
  resolvers             += "Sonatype Nexus Repository Manager".at("https://nexus.prod.makeorg.tech/repository/maven-public/"),
  scalastyleFailOnError := true,
  scalacOptions ++= Seq(
    "-Yrangepos",
    "-Xlint:_",
    "-Xlint:-strict-unsealed-patmat",
    "-Wconf:cat=lint-byname-implicit:s,cat=other-non-cooperative-equals:s,cat=w-flag-numeric-widen:s,any:e",
    "-encoding",
    "UTF-8",
    "-language:_",
    "-Ycache-plugin-class-loader:last-modified",
    "-Ycache-macro-class-loader:last-modified",
    "-Ybackend-parallelism",
    "5"
  ),
  IntegrationTest / scalastyleConfig        := (scalastyle / scalastyleConfig).value,
  IntegrationTest / scalastyleTarget        := target.value / "scalastyle-it-results.xml",
  IntegrationTest / scalastyleFailOnError   := (scalastyle / scalastyleFailOnError).value,
  IntegrationTest / scalastyleFailOnWarning := (scalastyle / scalastyleFailOnWarning).value,
  IntegrationTest / scalastyleSources       := Seq((IntegrationTest / scalaSource).value),
  IntegrationTest / testOptions             += Tests.Argument("-oF"),
  wartremoverErrors in (Compile, compile) ++= Warts.allBut(
    Wart.Any,
    Wart.DefaultArguments,
    Wart.Equals,
    Wart.GlobalExecutionContext,
    Wart.ImplicitConversion,
    Wart.ImplicitParameter,
    Wart.NonUnitStatements,
    Wart.Nothing,
    Wart.Overloading,
    Wart.PlatformDefault,
    Wart.StringPlusAny,
    Wart.ToString,
    Wart.Var
  )
) ++ Seq(Compile, Test).map { configuration =>
  scalacOptions.in(configuration, console) += "-Wconf:cat=lint-byname-implicit:s,cat=other-pure-statement:s,cat=unused-imports:s,cat=w-flag-numeric-widen:s,any:e"
} ++ inConfig(IntegrationTest)(scalafmtConfigSettings) ++ inConfig(IntegrationTest)(rawScalastyleSettings())

addCommandAlias("checkStyle", ";scalastyle;test:scalastyle;it:scalastyle;scalafmtCheckAll;scalafmtSbtCheck")
addCommandAlias("fixStyle", ";scalafmtAll;scalafmtSbt")

lazy val phantom = module("phantom", ".")
  .settings(moduleName := "make-phantom": _*)
  .aggregate(api, cockroachdb, core, integrationTests, persistence, technical, tests)

lazy val api = module("api")
  .settings(imageName := {
    val alias = dockerAlias.value
    s"${alias.registryHost.map(_ + "/").getOrElse("")}${alias.name}:${alias.tag.getOrElse("latest")}"
  })
  .dependsOn(cockroachdb, core, integrationTests % IntegrationTest, technical, tests % Test)

lazy val cockroachdb = module("cockroachdb")
  .dependsOn(persistence, technical, tests % Test)
  .dependsOn("integration-tests", IntegrationTest)

lazy val core = module("core")
  .dependsOn(technical)
  .dependsOn("tests", Test)

lazy val integrationTests = module("integration-tests")
  .dependsOn(cockroachdb, core, tests)

lazy val persistence = module("persistence")
  .dependsOn(core)

lazy val technical = module("technical")

lazy val tests = module("tests")
  .dependsOn(core)

ThisBuild / Test / fork            := true
ThisBuild / IntegrationTest / fork := true

isSnapshot in ThisBuild := false

git.formattedShaVersion := git.gitHeadCommit.value.map { sha =>
  sha.take(10)
}

version in ThisBuild := {
  git.formattedShaVersion.value.get
}

gitCommitMessageHook := Some(baseDirectory.value / "bin" / "commit-msg.hook")

enablePlugins(GitHooks)
enablePlugins(GitVersioning)
enablePlugins(SbtSwift)

swiftContainerName     := "reports"
swiftConfigurationPath := file("/var/run/secrets/main-api.conf")
swiftContainerDirectory := {
  val currentBranch: String = {
    if (Option(System.getenv("CI_COMMIT_REF_NAME")).exists(_.nonEmpty)) {
      System.getenv("CI_COMMIT_REF_NAME")
    } else {
      git.gitCurrentBranch.value
    }
  }
  Some(s"main-api/${LocalDate.now().toString}/$currentBranch/${version.value}")
}
swiftReportsToSendPath := {
  (Compile / crossTarget).value / "scoverage-report"
}

def module(id: String): Project = module(id, id)
def module(id: String, base: String): Project =
  Project(id, file(base))
    .configs(IntegrationTest)
    .settings(commonSettings: _*)
    .settings(Defaults.itSettings: _*)

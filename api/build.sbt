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

import java.time.{ZoneOffset, ZonedDateTime}

import com.typesafe.sbt.SbtGit.GitKeys

name := "make-api"

libraryDependencies ++= Seq(
  Dependencies.commonsLoggingBridge,
  Dependencies.log4jJul,
  Dependencies.kamonCore,
  Dependencies.kamonAkka,
  Dependencies.kamonAkkaHttp,
  Dependencies.kamonScalaFutures,
  Dependencies.kamonPrometheus,
  Dependencies.kamonSystemMetrics,
  Dependencies.kanela,
  Dependencies.akkaSlf4j,
  Dependencies.akkaPersistenceQuery,
  Dependencies.akkaHttp,
  Dependencies.akkaHttpSwagger,
  Dependencies.caliban,
  Dependencies.calibanAkkaHttp,
  Dependencies.cassandraQueryBuilder,
  Dependencies.zio,
  Dependencies.zioStreams,
  Dependencies.circeGeneric,
  Dependencies.swaggerUi,
  Dependencies.kafkaClients,
  Dependencies.avroSerializer,
  Dependencies.avro4s,
  Dependencies.elastic4s,
  Dependencies.elastic4sHttp,
  Dependencies.akkaHttpTest,
  Dependencies.akkaStreamTest,
  Dependencies.jerseyServer,
  Dependencies.jerseyHk2,
  Dependencies.jaxRsApi
)

lazy val swaggerUiVersion: SettingKey[String] =
  SettingKey[String]("swaggerUiVersion", "version of swagger ui").withRank(KeyRanks.Invisible)

swaggerUiVersion := {
  Dependencies.swaggerUiVersion
}

lazy val buildTime: SettingKey[String] = SettingKey[String]("buildTime", "time of build").withRank(KeyRanks.Invisible)

buildTime := ZonedDateTime.now(ZoneOffset.UTC).toString

buildInfoKeys :=
  Seq[BuildInfoKey](
    name,
    version,
    scalaVersion,
    sbtVersion,
    GitKeys.gitHeadCommit,
    GitKeys.gitCurrentBranch,
    buildTime,
    swaggerUiVersion
  )

run / fork := true

val kamonInstrumentationWorkaround = Seq("--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED")

javaOptions in run ++= Seq(
  "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005",
  "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager"
) ++ kamonInstrumentationWorkaround ++ SbtKanelaRunner.jvmForkOptions.value

javaOptions in Test            ++= kamonInstrumentationWorkaround
javaOptions in IntegrationTest ++= kamonInstrumentationWorkaround

enablePlugins(BuildInfoPlugin)

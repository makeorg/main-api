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
  Dependencies.kamonExecutors,
  Dependencies.kamonAkka,
  Dependencies.kamonAkkaHttp,
  Dependencies.kamonAnnotations,
  Dependencies.kamonScalaFutures,
  Dependencies.kamonPrometheus,
  Dependencies.kamonSystemMetrics,
  Dependencies.kanela,
  Dependencies.akkaSlf4j,
  Dependencies.akkaStream,
  Dependencies.akkaPersistence,
  Dependencies.akkaPersistenceQuery,
  Dependencies.akkaHttp,
  Dependencies.akkaHttpSwagger,
  Dependencies.akkaHttpCirce,
  Dependencies.akkaClusterSharding,
  Dependencies.akkaPersistenceCassandra,
  Dependencies.kryoSerializer,
  Dependencies.stamina,
  Dependencies.caliban,
  Dependencies.calibanAkkaHttp,
  Dependencies.zio,
  Dependencies.zioStreams,
  Dependencies.circeGeneric,
  Dependencies.constructr,
  Dependencies.constructrZookeeper,
  Dependencies.swaggerUi,
  Dependencies.kafkaClients,
  Dependencies.avroSerializer,
  Dependencies.avro4s,
  Dependencies.scalaOAuth,
  Dependencies.scalaBcrypt,
  Dependencies.scalike,
  Dependencies.scalikeMacros,
  Dependencies.swiftClient,
  Dependencies.postgresql,
  Dependencies.flywaydb,
  Dependencies.nettyAll,
  Dependencies.elastic4s,
  Dependencies.elastic4sHttp,
  Dependencies.elastic4sCirce,
  Dependencies.akkaHttpTest,
  Dependencies.akkaPersistenceInMemory,
  Dependencies.akkaTest,
  Dependencies.akkaStreamTest,
  Dependencies.dockerClient,
  Dependencies.dockerScalatest,
  Dependencies.jerseyServer,
  Dependencies.jerseyHk2,
  Dependencies.jaxRsApi,
  Dependencies.jsonLenses,
  Dependencies.apacheMath,
  Dependencies.staminaTestKit,
  Dependencies.alpakka
)

libraryDependencies += {
  if (System.getProperty("os.name").toLowerCase.contains("mac")) {
    Dependencies.nettyEpollMac
  } else {
    Dependencies.nettyEpoll
  }
}

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

fork in run             := true
fork in Test            := true
fork in IntegrationTest := true

javaOptions in run ++= Seq("-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager") ++ SbtKanelaRunner.jvmForkOptions.value

enablePlugins(BuildInfoPlugin)

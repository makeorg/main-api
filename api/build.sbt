import java.time.ZonedDateTime

import com.typesafe.sbt.SbtGit.GitKeys._

name := "make-api"

libraryDependencies ++= Seq(
  Dependencies.akkaHttp,
  Dependencies.akkaHttpSwagger,
  Dependencies.akkaHttpSprayJson,
  Dependencies.akkaClusterSharding,
  Dependencies.akkaPersistenceCassandra,
  Dependencies.akkaStreamKafka,
  Dependencies.swaggerUi,
  Dependencies.embeddedElasticSearch,
  Dependencies.kafkaClients,
  Dependencies.avroSerializer,
  Dependencies.avro4s,
  Dependencies.levelDB,
  Dependencies.levelDBJni,
  Dependencies.akkaHttpTest,
  Dependencies.scalaOAuth,
  Dependencies.scalikeAsync,
  Dependencies.scalikeAsyncPostgres,
  Dependencies.nettyAll,
  Dependencies.nettyEpoll,
  Dependencies.elastic4s,
  Dependencies.elastic4sHttp,
  Dependencies.elastic4sSprayJson,
  Dependencies.elastic4sCirce,
  Dependencies.elastic4sStream
)

lazy val now: SettingKey[ZonedDateTime] = SettingKey[ZonedDateTime]("now", "time of build")

now := {
  ZonedDateTime.now()
}

enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, gitHeadCommit, now)

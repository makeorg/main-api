import java.time.{ZoneOffset, ZonedDateTime}

import com.typesafe.sbt.SbtGit.GitKeys._

name := "make-api"

libraryDependencies ++= Seq(
  Dependencies.kamonJmx,
  Dependencies.kamonAkka,
  Dependencies.kamonAkkaHttp,
  Dependencies.kamonAkkaRemote,
  Dependencies.kamonScala,
  Dependencies.akkaSlf4j,
  Dependencies.akkaHttp,
  Dependencies.akkaHttpSwagger,
  Dependencies.akkaHttpCirce,
  Dependencies.akkaStreamCirce,
  Dependencies.aspectJWeaver,
  Dependencies.aspectJRt,
  Dependencies.circeGeneric,
  Dependencies.akkaClusterSharding,
  Dependencies.akkaPersistenceCassandra,
  Dependencies.akkaStreamKafka,
  Dependencies.swaggerUi,
  Dependencies.embeddedElasticSearch,
  Dependencies.kafkaClients,
  Dependencies.avroSerializer,
  Dependencies.avro4s,
  Dependencies.akkaHttpTest,
  Dependencies.scalaOAuth,
  Dependencies.scalike,
  Dependencies.postgresql,
  Dependencies.nettyAll,
  Dependencies.nettyEpoll,
  Dependencies.elastic4s,
  Dependencies.elastic4sHttp,
  Dependencies.elastic4sCirce,
  Dependencies.elastic4sStream
)

lazy val buildTime: SettingKey[ZonedDateTime] = SettingKey[ZonedDateTime]("buildTime", "time of build")


buildTime := {
  ZonedDateTime.now(ZoneOffset.UTC)
}


enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, gitHeadCommit, buildTime)

fork in run := true

aspectjSettings

javaOptions ++= (AspectjKeys.weaverOptions in Aspectj).value

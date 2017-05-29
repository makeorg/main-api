import java.time.{ZoneOffset, ZonedDateTime}

import com.typesafe.sbt.SbtGit.GitKeys._

name := "make-api"

libraryDependencies ++= Seq(
  Dependencies.kamonJmx,
  Dependencies.kamonAkka,
  Dependencies.kamonAkkaHttp,
  Dependencies.kamonAkkaRemote,
  Dependencies.kamonScala,
  Dependencies.aspectJWeaver,
  Dependencies.aspectJRt,
  Dependencies.akkaSlf4j,
  Dependencies.akkaHttp,
  Dependencies.akkaHttpSwagger,
  Dependencies.akkaHttpCirce,
  Dependencies.akkaStreamCirce,
  Dependencies.akkaClusterSharding,
  Dependencies.akkaPersistenceCassandra,
  Dependencies.akkaStreamKafka,
  Dependencies.circeGeneric,
  Dependencies.swaggerUi,
  Dependencies.kafkaClients,
  Dependencies.avroSerializer,
  Dependencies.avro4s,
  Dependencies.scalaOAuth,
  Dependencies.scalike,
  Dependencies.postgresql,
  Dependencies.nettyAll,
  Dependencies.nettyEpoll,
  Dependencies.elastic4s,
  Dependencies.elastic4sHttp,
  Dependencies.elastic4sCirce,
  Dependencies.elastic4sStream,
  Dependencies.akkaHttpTest,
  Dependencies.akkaPersistenceInMemory,
  Dependencies.akkaTest
)

lazy val buildTime: SettingKey[ZonedDateTime] =
  SettingKey[ZonedDateTime]("buildTime", "time of build")

buildTime := {
  ZonedDateTime.now(ZoneOffset.UTC)
}

enablePlugins(BuildInfoPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, gitHeadCommit, buildTime)

fork in run := true
fork in Test := true

aspectjSettings

javaOptions ++= (AspectjKeys.weaverOptions in Aspectj).value

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17")

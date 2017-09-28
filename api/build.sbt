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
  Dependencies.akkaClusterSharding,
  Dependencies.akkaPersistenceCassandra,
  Dependencies.stamina,
  Dependencies.circeGeneric,
  Dependencies.swaggerUi,
  Dependencies.kafkaClients,
  Dependencies.avroSerializer,
  Dependencies.avro4s,
  Dependencies.scalaOAuth,
  Dependencies.scalaBcrypt,
  Dependencies.scalike,
  Dependencies.scalikeTest,
  Dependencies.postgresql,
  Dependencies.nettyAll,
  Dependencies.elastic4s,
  Dependencies.elastic4sHttp,
  Dependencies.elastic4sCirce,
  Dependencies.akkaHttpTest,
  Dependencies.akkaPersistenceInMemory,
  Dependencies.akkaTest,
  Dependencies.dockerClient,
  Dependencies.dockerScalatest,
  Dependencies.jerseyServer,
  Dependencies.jaxRsApi,
  Dependencies.wireMock,
  Dependencies.semantic
)

libraryDependencies += {
  if (System.getProperty("os.name").toLowerCase.contains("mac")) {
    Dependencies.nettyEpollMac
  } else {
    Dependencies.nettyEpoll
  }
}

lazy val swaggerUiVersion: SettingKey[String] =
  SettingKey[String]("swaggerUiVersion", "version of swagger ui")

swaggerUiVersion := {
  Dependencies.swaggerUiVersion
}

lazy val buildTime: SettingKey[String] = SettingKey[String]("buildTime", "time of build")

buildTime := ZonedDateTime.now(ZoneOffset.UTC).toString

enablePlugins(BuildInfoPlugin)
enablePlugins(SbtAspectj)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, gitHeadCommit, buildTime, swaggerUiVersion)

fork in run := true
fork in Test := true
fork in IntegrationTest := true

javaOptions in run ++= (aspectjWeaverOptions in Aspectj).value
javaOptions in run += "-Dconfig.resource=default-application.conf"

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17")

import java.time.{ZoneOffset, ZonedDateTime}

import com.typesafe.sbt.SbtGit.GitKeys

name := "make-api"

libraryDependencies ++= Seq(
  Dependencies.commonsLoggingBridge,
  Dependencies.log4jJul,
  Dependencies.kamonAkka,
  Dependencies.kamonAkkaHttp,
  Dependencies.kamonAkkaRemote,
  Dependencies.kamonScala,
  Dependencies.kamonPrometheus,
  Dependencies.kamonSystemMetrics,
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
  Dependencies.constructr,
  Dependencies.constructrZookeeper,
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
  Dependencies.semantic,
  Dependencies.apacheMath
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

fork in run := true
fork in Test := true
fork in IntegrationTest := true

javaOptions in run ++= (aspectjWeaverOptions in Aspectj).value
javaOptions in run ++= Seq(
  "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager"
)

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17")

enablePlugins(BuildInfoPlugin)
enablePlugins(SbtAspectj)

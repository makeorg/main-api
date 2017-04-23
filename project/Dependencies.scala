import sbt._

object Dependencies {

  private val akkaVersion = "2.5.0"
  private val akkaHttpVersion = "10.0.5"
  private val kafkaVersion = "0.10.2.0"

  val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
  val logger: ModuleID = "org.apache.logging.log4j" % "log4j" % "2.8.2"
  val loggerBridge: ModuleID = "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.8.2"

  val akkaPersistence: ModuleID = "com.typesafe.akka" %% "akka-persistence" % akkaVersion
  val akkaHttp: ModuleID = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttpSprayJson: ModuleID = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
  val akkaCluster: ModuleID = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  val akkaClusterTools: ModuleID = "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion
  val akkaClusterSharding: ModuleID = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
  val akkaPersistenceCassandra: ModuleID = 	"com.typesafe.akka" %% "akka-persistence-cassandra" % "0.50"
  val akkaHttpSwagger: ModuleID = "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.9.1"
  val swaggerUi: ModuleID = "org.webjars" % "swagger-ui" % "2.2.8"
  val levelDB: ModuleID = "org.iq80.leveldb"            % "leveldb"          % "0.7"
  val levelDBJni: ModuleID = "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"

  val kafkaClients: ModuleID = "org.apache.kafka" % "kafka-clients" % kafkaVersion

  val avro4s: ModuleID = "com.sksamuel.avro4s" %% "avro4s-core" % "1.6.4"
  val avroSerializer: ModuleID = ("io.confluent" % "kafka-avro-serializer" % "3.2.0").exclude("org.slf4j", "slf4j-log4j12")

  val configuration: ModuleID = "com.typesafe" % "config" % "1.3.1"

  val embeddedElasticSearch: ModuleID = "pl.allegro.tech" % "embedded-elasticsearch" % "2.1.0"

  val scalaTest: ModuleID = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  val akkaTest: ModuleID = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
  val akkaHttpTest: ModuleID = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"
  val mockito: ModuleID = "org.mockito" % "mockito-core" % "2.7.22" % "test"

}
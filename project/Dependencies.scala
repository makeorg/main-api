import sbt._

object Dependencies {

  private val akkaVersion = "2.5.0"
  private val akkaHttpVersion = "10.0.5"

  val akkaPersistence: ModuleID = "com.typesafe.akka" %% "akka-persistence" % akkaVersion
  val akkaHttp: ModuleID = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttpSprayJson: ModuleID = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
  val akkaCluster: ModuleID = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  val akkaClusterTools: ModuleID = "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion
  val akkaClusterSharding: ModuleID = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
  val akkaPersistenceCassandra: ModuleID = 	"com.typesafe.akka" %% "akka-persistence-cassandra" % "0.50"

  val configuration: ModuleID = "com.typesafe" % "config" % "1.3.1"

  val embeddedElasticSearch: ModuleID = "pl.allegro.tech" % "embedded-elasticsearch" % "2.1.0"

  val scalaTest: ModuleID = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  val akkaTest: ModuleID = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
  val akkaHttpTest: ModuleID = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % "test"
  val mockito: ModuleID = "org.mockito" % "mockito-core" % "2.7.22" % "test"

}
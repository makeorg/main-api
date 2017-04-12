import sbt._

object Dependencies {

  private val finagleVersion = "6.43.0"
  private val finchVersion = "0.14.0"
  private val akkaVersion = "2.5.0-RC2"

  val finagleHttp: ModuleID = "com.twitter" %% "finagle-http" % finagleVersion
  val finchCirce: ModuleID = "com.github.finagle" %% "finch-circe" % finchVersion
  val twitterServer: ModuleID = "com.twitter" %% "twitter-server" % "1.28.0"
  val circeGeneric: ModuleID = "io.circe" %% "circe-generic" % "0.7.0"

  val akkaPersistence: ModuleID = "com.typesafe.akka" %% "akka-persistence" % akkaVersion
  val akkaCluster: ModuleID = "com.typesafe.akka" %% "akka-cluster" % akkaVersion
  val akkaClusterTools: ModuleID = "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion
  val akkaClusterSharding: ModuleID = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion

  val configuration: ModuleID = "com.typesafe" % "config" % "1.3.1"

  val scalaTest: ModuleID = "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  val akkaTest: ModuleID = "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"

}
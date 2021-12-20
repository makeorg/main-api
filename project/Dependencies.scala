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

import sbt._

object Dependencies {

  private val akkaVersion = "2.6.15"
  private val akkaHttpVersion = "10.2.5"
  private val alpakkaVersion = "1.1.2"
  private val nettyVersion = "4.1.66.Final"
  private val kafkaVersion = "1.1.0"
  private val elastic4sVersion = "6.7.8"
  private val enumeratumVersion = "1.7.0"
  private val jerseyVersion = "2.32"
  private val kamonVersion = "2.2.3"
  private val log4jVersion = "2.17.0"
  private val circeVersion = "0.14.1"
  val kanelaVersion: String = "1.0.11"
  val swaggerUiVersion: String = "3.20.9"
  private val refinedVersion: String = "0.9.27"
  private val scalikeVersion = "3.5.0"
  private val staminaVersion: String = "0.1.5+1-74109b8e"
  private val calibanVersion = "0.9.1"
  private val zioVersion = "1.0.1"
  private val mockitoVersion = "1.16.37"

  val cats: ModuleID = "org.typelevel" %% "cats-core" % "2.6.1"

  val grizzledSlf4j: ModuleID = "org.clapper"             %% "grizzled-slf4j"  % "1.3.4"
  val logger: ModuleID = "org.apache.logging.log4j"       % "log4j"            % log4jVersion
  val loggerBridge: ModuleID = "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4jVersion
  val commonsLoggingBridge: ModuleID = "org.slf4j"        % "jcl-over-slf4j"   % "1.7.32"
  val log4jJul: ModuleID = "org.apache.logging.log4j"     % "log4j-jul"        % log4jVersion

  val nettyEpoll: ModuleID = ("io.netty" % "netty-transport-native-epoll" % nettyVersion).classifier("linux-x86_64")
  val nettyEpollMac: ModuleID =
    ("io.netty" % "netty-transport-native-kqueue" % nettyVersion).classifier("osx-x86_64")
  val nettyAll: ModuleID = "io.netty" % "netty-all" % nettyVersion

  val circeGeneric: ModuleID = "io.circe"                 %% "circe-generic"               % circeVersion
  val circeParser = "io.circe"                            %% "circe-parser"                % circeVersion
  val akkaClusterSharding: ModuleID = "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion
  val akkaHttp: ModuleID = "com.typesafe.akka"            %% "akka-http"                   % akkaHttpVersion
  val akkaHttpCirce: ModuleID = "de.heikoseeberger"       %% "akka-http-circe"             % "1.37.0"
  val akkaHttpSwagger: ModuleID = ("com.github.swagger-akka-http" %% "swagger-akka-http" % "1.2.0")
    .exclude("javax.ws.rs", "jsr311-api")
  val akkaPersistence: ModuleID = "com.typesafe.akka"      %% "akka-persistence-typed" % akkaVersion
  val akkaPersistenceQuery: ModuleID = "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion
  val akkaPersistenceCassandra: ModuleID =
    ("com.typesafe.akka" %% "akka-persistence-cassandra" % "1.0.5")
      .exclude("io.netty", "netty-handler")
  val akkaSlf4j: ModuleID = "com.typesafe.akka"            %% "akka-slf4j"               % akkaVersion
  val alpakka: ModuleID = "com.lightbend.akka"             %% "akka-stream-alpakka-file" % alpakkaVersion
  val jaxRsApi: ModuleID = "javax.ws.rs"                   % "javax.ws.rs-api"           % "2.0.1"
  val cassandraQueryBuilder: ModuleID = "com.datastax.oss" % "java-driver-query-builder" % "4.6.1"

  val kryoSerializer: ModuleID = "io.altoo" %% "akka-kryo-serialization" % "1.1.5"

  val swaggerUi: ModuleID = "org.webjars" % "swagger-ui" % swaggerUiVersion

  val kamonCore: ModuleID = ("io.kamon" %% "kamon-core" % kamonVersion).exclude("ch.qos.logback", "logback-classic")
  val kamonExecutors: ModuleID =
    ("io.kamon" %% "kamon-executors" % kamonVersion).exclude("ch.qos.logback", "logback-classic")
  val kamonAkka: ModuleID =
    ("io.kamon" %% "kamon-akka" % kamonVersion).exclude("ch.qos.logback", "logback-classic")
  val kamonScalaFutures: ModuleID = "io.kamon" %% "kamon-scala-future" % kamonVersion
  val kamonAkkaHttp: ModuleID =
    ("io.kamon" %% "kamon-akka-http" % kamonVersion).exclude("ch.qos.logback", "logback-classic")
  val kamonSystemMetrics: ModuleID = "io.kamon" %% "kamon-system-metrics" % kamonVersion
  val kamonPrometheus: ModuleID = "io.kamon"    %% "kamon-prometheus"     % kamonVersion
  val kamonAnnotations: ModuleID = "io.kamon"   %% "kamon-annotation"     % kamonVersion

  val kanela: ModuleID = "io.kamon" % "kanela-agent" % kanelaVersion

  val constructr: ModuleID = "org.make.constructr" %% "constructr" % "0.20.0"
  val constructrZookeeper: ModuleID =
    ("org.make.constructr" %% "constructr-coordination-zookeeper" % "0.5.0").exclude("log4j", "log4j")

  val scalaOAuth: ModuleID = "com.nulab-inc"      %% "scala-oauth2-core" % "1.5.0"
  val scalaBcrypt: ModuleID = "com.github.t3hnar" %% "scala-bcrypt"      % "4.1"

  val scalike: ModuleID = "org.scalikejdbc"       %% "scalikejdbc"                      % scalikeVersion
  val scalikeMacros: ModuleID = "org.scalikejdbc" %% "scalikejdbc-syntax-support-macro" % scalikeVersion
  val postgresql: ModuleID = "org.postgresql"     % "postgresql"                        % "42.2.23"
  val flywaydb: ModuleID = "org.flywaydb"         % "flyway-core"                       % "7.7.3"

  val slugify: ModuleID = "com.github.slugify" % "slugify" % "2.5"

  val swiftClient: ModuleID = "org.make" %% "openstack-swift-client" % "1.0.17"

  val jsoup: ModuleID = "org.jsoup" % "jsoup" % "1.14.1"

  // Kafka + AVRO
  val kafkaClients: ModuleID = "org.apache.kafka" % "kafka-clients" % kafkaVersion
  val avro4s: ModuleID = "com.sksamuel.avro4s"    %% "avro4s-core"  % "3.0.5.make2"
  val avroSerializer: ModuleID =
    ("io.confluent" % "kafka-avro-serializer" % "3.2.2")
      .exclude("org.slf4j", "slf4j-log4j12")
      .exclude("io.netty", "netty")

  val configuration: ModuleID = "com.typesafe" % "config" % "1.4.0"

  val elastic4s: ModuleID = "com.sksamuel.elastic4s"      %% "elastic4s-core"  % elastic4sVersion
  val elastic4sHttp: ModuleID = "com.sksamuel.elastic4s"  %% "elastic4s-http"  % elastic4sVersion
  val elastic4sCirce: ModuleID = "com.sksamuel.elastic4s" %% "elastic4s-circe" % elastic4sVersion

  val stamina: ModuleID = "com.scalapenos"    %% "stamina-json" % staminaVersion
  val jsonLenses = "net.virtual-void"         %% "json-lenses"  % "0.6.2"
  val scalaCheck: ModuleID = "org.scalacheck" %% "scalacheck"   % "1.15.4"

  val zio: ModuleID = "dev.zio"                           %% "zio"               % zioVersion
  val zioStreams: ModuleID = "dev.zio"                    %% "zio-streams"       % zioVersion
  val caliban: ModuleID = "com.github.ghostdogpr"         %% "caliban"           % calibanVersion
  val calibanAkkaHttp: ModuleID = "com.github.ghostdogpr" %% "caliban-akka-http" % calibanVersion

  val apacheMath: ModuleID = "org.apache.commons" % "commons-math3" % "3.6.1"

  val refinedScala: ModuleID = "eu.timepit"      %% "refined"            % refinedVersion
  val refinedCirce: ModuleID = "io.circe"        %% "circe-refined"      % circeVersion
  val refinedScalaCheck: ModuleID = "eu.timepit" %% "refined-scalacheck" % refinedVersion

  val enumeratum: ModuleID = "com.beachape"           %% "enumeratum"            % enumeratumVersion
  val enumeratumCirce: ModuleID = "com.beachape"      %% "enumeratum-circe"      % enumeratumVersion
  val enumeratumScalacheck: ModuleID = "com.beachape" %% "enumeratum-scalacheck" % enumeratumVersion

  // Test related dependencies
  val akkaTest: ModuleID = "com.typesafe.akka"            %% "akka-actor-testkit-typed" % akkaVersion
  val akkaStreamTest: ModuleID = "com.typesafe.akka"      %% "akka-stream-testkit"      % akkaVersion % "it,test"
  val scalaTest: ModuleID = "org.scalatest"               %% "scalatest"                % "3.2.9"
  val scalaTestScalaCheck: ModuleID = "org.scalatestplus" %% "scalacheck-1-15"          % "3.2.9.0"
  val akkaHttpTest: ModuleID = "com.typesafe.akka"        %% "akka-http-testkit"        % akkaHttpVersion % "it,test"
  val mockito: ModuleID = "org.mockito"                   %% "mockito-scala"            % mockitoVersion
  val mockitoScalatest: ModuleID = "org.mockito"          %% "mockito-scala-scalatest"  % mockitoVersion
  val dockerScalatest: ModuleID = "com.whisk"             %% "docker-testkit-scalatest" % "0.9.9"
  val dockerClient: ModuleID = ("com.whisk" %% "docker-testkit-impl-docker-java" % "0.9.9")
    .exclude("io.netty", "netty-handler")
    .exclude("io.netty", "netty-transport-native-epoll")

  // Needed to use the client....
  val jerseyServer: ModuleID = "org.glassfish.jersey.core"      % "jersey-server"              % jerseyVersion % "it"
  val jerseyHk2: ModuleID = "org.glassfish.jersey.inject"       % "jersey-hk2"                 % jerseyVersion % "it"
  val akkaPersistenceInMemory: ModuleID = "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2"
  val staminaTestKit: ModuleID = "com.scalapenos"               %% "stamina-testkit"           % staminaVersion % "test"

  val scalaCsv: ModuleID = "com.github.tototoshi"    %% "scala-csv"        % "1.3.8"
  val scalacacheGuava: ModuleID = "com.github.cb372" %% "scalacache-guava" % "0.28.0"
}

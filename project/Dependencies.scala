import sbt._

object Dependencies {

  private val akkaVersion = "2.5.12"
  private val akkaHttpVersion = "10.1.1"
  private val nettyVersion = "4.1.20.Final"
  private val kafkaVersion = "1.1.0"
  private val elastic4sVersion = "5.6.1"
  private val kamonVersion = "1.1.2"
  private val kamonAkkaVersion = "1.0.1"
  private val kamonAkkaHttpVersion = "1.1.0"
  private val kamonExecutorsVersion = "1.0.1"
  private val kamonScalaFuturesVersion = "1.0.0"
  private val kamonAkkaRemoteVersion = "1.0.1"
  private val kamonSystemMetricsVersion = "1.0.0"
  private val kamonPrometheusVersion = "1.1.1"
  private val circeVersion = "0.9.1"
  val aspectJVersion: String = "1.8.13"
  val swaggerUiVersion: String = "3.14.0"

  val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.0"
  val logger: ModuleID = "org.apache.logging.log4j"         % "log4j"            % "2.11.0"
  val loggerBridge: ModuleID = "org.apache.logging.log4j"   % "log4j-slf4j-impl" % "2.11.0"
  val commonsLoggingBridge: ModuleID = "org.slf4j"          % "jcl-over-slf4j"   % "1.7.25"
  val log4jJul: ModuleID = "org.apache.logging.log4j"       % "log4j-jul"        % "2.11.0"

  val nettyEpoll: ModuleID = ("io.netty" % "netty-transport-native-epoll" % nettyVersion).classifier("linux-x86_64")
  val nettyEpollMac: ModuleID =
    ("io.netty" % "netty-transport-native-kqueue" % nettyVersion).classifier("macos-x86_64")
  val nettyAll: ModuleID = "io.netty" % "netty-all" % nettyVersion

  val circeGeneric: ModuleID = "io.circe" %% "circe-generic" % circeVersion

  val akka: ModuleID = "com.typesafe.akka"                %% "akka-actor"            % akkaVersion
  val akkaCluster: ModuleID = "com.typesafe.akka"         %% "akka-cluster"          % akkaVersion
  val akkaClusterTools: ModuleID = "com.typesafe.akka"    %% "akka-cluster-tools"    % akkaVersion
  val akkaClusterSharding: ModuleID = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
  val akkaStream: ModuleID = "com.typesafe.akka"          %% "akka-stream"           % akkaVersion
  val akkaHttp: ModuleID = "com.typesafe.akka"            %% "akka-http"             % akkaHttpVersion
  val akkaHttpCirce: ModuleID = "de.knutwalker"           %% "akka-http-circe"       % "3.5.0"
  val akkaHttpSwagger: ModuleID = ("com.github.swagger-akka-http" %% "swagger-akka-http" % "0.14.0")
    .exclude("javax.ws.rs", "jsr311-api")
  val akkaPersistence: ModuleID = "com.typesafe.akka" %% "akka-persistence" % akkaVersion
  val akkaPersistenceCassandra: ModuleID =
    ("com.typesafe.akka" %% "akka-persistence-cassandra" % "0.80")
      .exclude("io.netty", "netty-handler")
  val akkaSlf4j: ModuleID = "com.typesafe.akka"          %% "akka-slf4j"              % akkaVersion
  val jaxRsApi: ModuleID = "javax.ws.rs"                 % "javax.ws.rs-api"          % "2.0.1"
  val kryoSerializer: ModuleID = "com.github.romix.akka" %% "akka-kryo-serialization" % "0.5.2"

  val swaggerUi: ModuleID = "org.webjars" % "swagger-ui" % swaggerUiVersion

  val kamonCore: ModuleID = ("io.kamon" %% "kamon-core" % kamonVersion).exclude("ch.qos.logback", "logback-classic")
  val kamonExecutors: ModuleID =
    ("io.kamon" %% "kamon-executors" % kamonExecutorsVersion).exclude("ch.qos.logback", "logback-classic")
  val kamonAkka: ModuleID =
    ("io.kamon" %% "kamon-akka-2.5" % kamonAkkaVersion).exclude("ch.qos.logback", "logback-classic")
  val kamonScalaFutures: ModuleID = "io.kamon" %% "kamon-scala-future" % kamonScalaFuturesVersion
  val kamonAkkaHttp: ModuleID =
    ("io.kamon" %% "kamon-akka-http-2.5" % kamonAkkaHttpVersion).exclude("ch.qos.logback", "logback-classic")
  val kamonAkkaRemote: ModuleID =
    ("io.kamon" %% "kamon-akka-remote-2.5" % kamonAkkaRemoteVersion).exclude("ch.qos.logback", "logback-classic")
  val kamonSystemMetrics: ModuleID = "io.kamon" %% "kamon-system-metrics" % kamonSystemMetricsVersion
  val kamonPrometheus: ModuleID = "io.kamon"    %% "kamon-prometheus"     % kamonPrometheusVersion

  val aspectJWeaver: ModuleID = "org.aspectj" % "aspectjweaver" % aspectJVersion
  val aspectJRt: ModuleID = "org.aspectj"     % "aspectjrt"     % aspectJVersion

  val constructr: ModuleID = "de.heikoseeberger" %% "constructr" % "0.19.0"
  val constructrZookeeper: ModuleID =
    ("com.lightbend.constructr" %% "constructr-coordination-zookeeper" % "0.4.1").exclude("log4j", "log4j")

  val scalaOAuth: ModuleID = "com.nulab-inc"      %% "scala-oauth2-core" % "1.3.0"
  val scalaBcrypt: ModuleID = "com.github.t3hnar" %% "scala-bcrypt"      % "3.1"

  val scalike: ModuleID = "org.scalikejdbc"   %% "scalikejdbc" % "3.2.3"
  val postgresql: ModuleID = "org.postgresql" % "postgresql"   % "42.2.2"
  val flywaydb: ModuleID = "org.flywaydb"     % "flyway-core"  % "5.1.1"

  val slugify: ModuleID = "com.github.slugify" % "slugify" % "2.2"

  // Kafka + AVRO
  val kafkaClients: ModuleID = "org.apache.kafka" % "kafka-clients" % kafkaVersion
  val avro4s: ModuleID = "com.sksamuel.avro4s"    %% "avro4s-core"  % "1.8.3"
  val avroSerializer: ModuleID =
    ("io.confluent" % "kafka-avro-serializer" % "3.2.2")
      .exclude("org.slf4j", "slf4j-log4j12")
      .exclude("io.netty", "netty")

  val configuration: ModuleID = "com.typesafe" % "config" % "1.3.3"

  val elastic4s: ModuleID = "com.sksamuel.elastic4s"      %% "elastic4s-core"  % elastic4sVersion
  val elastic4sHttp: ModuleID = "com.sksamuel.elastic4s"  %% "elastic4s-http"  % elastic4sVersion
  val elastic4sCirce: ModuleID = "com.sksamuel.elastic4s" %% "elastic4s-circe" % elastic4sVersion

  val stamina: ModuleID = "com.scalapenos" %% "stamina-json" % "0.1.4"
  val sprayJson: ModuleID = "io.spray"     %% "spray-json"   % "1.3.4"

  // Test related dependencies
  val akkaTest: ModuleID = "com.typesafe.akka"     %% "akka-testkit"             % akkaVersion     % "it,test"
  val scalaTest: ModuleID = "org.scalatest"        %% "scalatest"                % "3.0.5"         % "it,test"
  val akkaHttpTest: ModuleID = "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % "it,test"
  val mockito: ModuleID = "org.mockito"            % "mockito-core"              % "2.13.0"        % "it,test"
  val dockerScalatest: ModuleID = "com.whisk"      %% "docker-testkit-scalatest" % "0.9.6"         % "it"
  val dockerClient: ModuleID = ("com.whisk" %% "docker-testkit-impl-docker-java" % "0.9.6" % "it")
    .exclude("io.netty", "netty-handler")
    .exclude("io.netty", "netty-transport-native-epoll")
  val wireMock: ModuleID = "com.github.tomakehurst" % "wiremock" % "2.14.0" % "test"

  // Needed to use the client....
  val jerseyServer: ModuleID = "org.glassfish.jersey.core"      % "jersey-server"              % "2.26"    % "it"
  val jerseyHk2: ModuleID = "org.glassfish.jersey.inject"       % "jersey-hk2"                 % "2.26"    % "it"
  val akkaPersistenceInMemory: ModuleID = "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.1" % "it,test"
  val staminaTestKit: ModuleID = "com.scalapenos"               %% "stamina-testkit"           % "0.1.4"   % "test"

  // Fixtures
  val gatlingHighcharts: ModuleID = "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.3.0" % "test"
  val gatling: ModuleID = "io.gatling"                      % "gatling-test-framework"    % "2.3.0" % "test"

  // apache math
  val apacheMath: ModuleID = "org.apache.commons" % "commons-math3" % "3.6.1"
}

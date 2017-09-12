import sbt._

object Dependencies {

  private val akkaVersion = "2.5.3"
  private val akkaHttpVersion = "10.0.9"
  private val nettyVersion = "4.1.12.Final"
  private val kafkaVersion = "0.11.0.0"
  private val elastic4sVersion = "5.4.1"
  private val kamonVersion = "0.6.6"
  private val circeVersion = "0.8.0"
  val aspectJVersion: String = "1.8.10"
  val swaggerUiVersion: String = "3.1.4"

  val scalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging"   % "3.5.0"
  val logger: ModuleID = "org.apache.logging.log4j"         % "log4j"            % "2.8.2"
  val loggerBridge: ModuleID = "org.apache.logging.log4j"   % "log4j-slf4j-impl" % "2.8.2"

  val nettyEpoll: ModuleID = ("io.netty" % "netty-transport-native-epoll" % nettyVersion).classifier("linux-x86_64")
  val nettyEpollMac: ModuleID =
    ("io.netty" % "netty-transport-native-kqueue" % nettyVersion).classifier("macos-x86_64")
  val nettyAll: ModuleID = "io.netty" % "netty-all" % nettyVersion

  val circeGeneric: ModuleID = "io.circe" %% "circe-generic" % circeVersion

  val akka: ModuleID = "com.typesafe.akka"                %% "akka-actor"            % akkaVersion
  val akkaCluster: ModuleID = "com.typesafe.akka"         %% "akka-cluster"          % akkaVersion
  val akkaClusterTools: ModuleID = "com.typesafe.akka"    %% "akka-cluster-tools"    % akkaVersion
  val akkaClusterSharding: ModuleID = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
  val akkaHttp: ModuleID = "com.typesafe.akka"            %% "akka-http"             % akkaHttpVersion
  val akkaHttpCirce: ModuleID = "de.knutwalker"           %% "akka-http-circe"       % "3.3.0"
  val akkaHttpSwagger: ModuleID = ("com.github.swagger-akka-http" %% "swagger-akka-http" % "0.10.0")
    .exclude("javax.ws.rs", "jsr311-api")
  val akkaPersistence: ModuleID = "com.typesafe.akka" %% "akka-persistence" % akkaVersion
  val akkaPersistenceCassandra: ModuleID =
    ("com.typesafe.akka" %% "akka-persistence-cassandra" % "0.54")
      .exclude("io.netty", "netty-handler")
  val akkaSlf4j: ModuleID = "com.typesafe.akka"   %% "akka-slf4j"        % akkaVersion
  val akkaStreamCirce: ModuleID = "de.knutwalker" %% "akka-stream-circe" % "3.3.0"
  val akkaStreamKafka: ModuleID =
    ("com.typesafe.akka" %% "akka-stream-kafka" % "0.16")
      .exclude("org.apache.kafka", "kafka-clients")
  val jaxRsApi: ModuleID = "javax.ws.rs" % "javax.ws.rs-api" % "2.0.1"

  val swaggerUi: ModuleID = "org.webjars" % "swagger-ui" % swaggerUiVersion

  val kamonAkka: ModuleID = "io.kamon" %% "kamon-akka-2.4" % kamonVersion
  val kamonAkkaHttp: ModuleID =
    ("io.kamon" %% "kamon-akka-http" % kamonVersion)
      .exclude("com.typesafe.akka", "akka-http_2.12")
      .exclude("com.typesafe.akka", "akka-actor_2.12")
  val kamonAkkaRemote: ModuleID = "io.kamon" %% "kamon-akka-remote-2.4" % kamonVersion
  val kamonJmx: ModuleID = "io.kamon"        %% "kamon-jmx"             % kamonVersion
  val kamonScala: ModuleID = "io.kamon"      %% "kamon-scala"           % kamonVersion

  val aspectJWeaver: ModuleID = "org.aspectj" % "aspectjweaver" % aspectJVersion
  val aspectJRt: ModuleID = "org.aspectj"     % "aspectjrt"     % aspectJVersion

  val scalaOAuth: ModuleID = "com.nulab-inc"      %% "scala-oauth2-core" % "1.3.0"
  val scalaBcrypt: ModuleID = "com.github.t3hnar" %% "scala-bcrypt"      % "3.0"

  val scalike: ModuleID = "org.scalikejdbc"   %% "scalikejdbc" % "3.0.1"
  val postgresql: ModuleID = "org.postgresql" % "postgresql"   % "42.1.0"

  val slugify: ModuleID = "com.github.slugify" % "slugify" % "2.1.9"

  // Kafka + AVRO
  val kafkaClients: ModuleID = "org.apache.kafka" % "kafka-clients" % kafkaVersion
  val avro4s: ModuleID = "com.sksamuel.avro4s"    %% "avro4s-core"  % "1.6.4"
  val avroSerializer: ModuleID =
    ("io.confluent" % "kafka-avro-serializer" % "3.2.0")
      .exclude("org.slf4j", "slf4j-log4j12")
      .exclude("io.netty", "netty")

  val configuration: ModuleID = "com.typesafe" % "config" % "1.3.1"

  val elastic4s: ModuleID = "com.sksamuel.elastic4s"      %% "elastic4s-core"  % elastic4sVersion
  val elastic4sHttp: ModuleID = "com.sksamuel.elastic4s"  %% "elastic4s-http"  % elastic4sVersion
  val elastic4sCirce: ModuleID = "com.sksamuel.elastic4s" %% "elastic4s-circe" % elastic4sVersion
  val elastic4sStream: ModuleID = ("com.sksamuel.elastic4s" %% "elastic4s-streams" % elastic4sVersion)
    .exclude("io.netty", "netty-all")

  val stamina: ModuleID = "com.scalapenos" %% "stamina-json" % "0.1.3"

  // Test related dependencies
  val akkaTest: ModuleID = "com.typesafe.akka"     %% "akka-testkit"             % akkaVersion     % "it,test"
  val scalaTest: ModuleID = "org.scalatest"        %% "scalatest"                % "3.0.1"         % "it,test"
  val akkaHttpTest: ModuleID = "com.typesafe.akka" %% "akka-http-testkit"        % akkaHttpVersion % "it,test"
  val mockito: ModuleID = "org.mockito"            % "mockito-core"              % "2.7.22"        % "it,test"
  val scalikeTest: ModuleID = "org.scalikejdbc"    %% "scalikejdbc-test"         % "3.0.1"         % "it,test"
  val dockerScalatest: ModuleID = "com.whisk"      %% "docker-testkit-scalatest" % "0.9.5"         % "it"
  val dockerClient: ModuleID = ("com.whisk" %% "docker-testkit-impl-docker-java" % "0.9.5" % "it")
    .exclude("io.netty", "netty-handler")
    .exclude("io.netty", "netty-transport-native-epoll")
  val wireMock: ModuleID = "com.github.tomakehurst" % "wiremock" % "2.6.0"

  // Needed to use the client....
  val jerseyServer: ModuleID = "org.glassfish.jersey.core"      % "jersey-server"              % "2.23.1"  % "it"
  val akkaPersistenceInMemory: ModuleID = "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.0" % "it,test"
}

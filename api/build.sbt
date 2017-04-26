name := "make-api"

libraryDependencies ++= Seq(
  Dependencies.akkaHttp,
  Dependencies.akkaHttpSwagger,
  Dependencies.akkaHttpSprayJson,
  Dependencies.akkaClusterSharding,
  Dependencies.akkaPersistenceCassandra,
  Dependencies.swaggerUi,
  Dependencies.embeddedElasticSearch,
  Dependencies.kafkaClients,
  Dependencies.avroSerializer,
  Dependencies.avro4s,
  Dependencies.levelDB,
  Dependencies.levelDBJni,
  Dependencies.akkaHttpTest,
  Dependencies.scalaOAuth,
  Dependencies.akkaHttpOAuth,
  Dependencies.scalikeAsync,
  Dependencies.scalikeAsyncPostgres,
  Dependencies.nettyAll,
  Dependencies.nettyEpoll
)

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
  Dependencies.akkaHttpTest
)

mainClass in assembly := Some("org.make.api.MakeApi")

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", other @_*) => other.map(_.toLowerCase) match {
    case "manifest.mf" :: Nil => MergeStrategy.discard
    case "webjars" :: _ => MergeStrategy.first
    case _ => MergeStrategy.first
  }
  case _ => MergeStrategy.first
}

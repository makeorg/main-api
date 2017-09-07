name := "make-core"

libraryDependencies ++= Seq(
  Dependencies.akkaPersistence,
  Dependencies.akkaClusterSharding,
  Dependencies.akkaHttpSwagger, // TODO: import only swagger not akka-http
  Dependencies.elastic4s,
  Dependencies.elastic4sHttp,
  Dependencies.avro4s,
  Dependencies.circeGeneric,
  Dependencies.slugify
)

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17")

name := "make-core"

libraryDependencies ++= Seq(
  Dependencies.akkaPersistence,
  Dependencies.akkaClusterSharding,
  Dependencies.avro4s,
  Dependencies.circeGeneric
)

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17")

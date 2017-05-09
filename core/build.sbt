
name := "make-core"

libraryDependencies ++= Seq(
  Dependencies.akkaPersistence,
  Dependencies.akkaClusterSharding,
  Dependencies.avro4s,
  Dependencies.circeGeneric

)
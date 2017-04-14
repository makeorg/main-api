name := "make-api"

libraryDependencies ++= Seq(
  Dependencies.akkaHttp,
  Dependencies.akkaHttpSprayJson,
  Dependencies.akkaClusterSharding,
  Dependencies.akkaPersistenceCassandra,
  Dependencies.embeddedElasticSearch,
  "org.iq80.leveldb"            % "leveldb"          % "0.7",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8",
  Dependencies.akkaHttpTest
)
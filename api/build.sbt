name := "make-api"

libraryDependencies ++= Seq(
  Dependencies.finchCirce,
  Dependencies.circeGeneric,
  Dependencies.twitterServer,
  Dependencies.twitterServer,
  Dependencies.akkaClusterSharding,
  "org.iq80.leveldb"            % "leveldb"          % "0.7",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"
)
name := "make-api"

libraryDependencies ++= Seq(
  Dependencies.akka,
  Dependencies.nettyAll,
  Dependencies.nettyEpoll,
  Dependencies.akkaClusterSharding
)

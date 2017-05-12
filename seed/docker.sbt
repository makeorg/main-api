enablePlugins(UniversalPlugin)
enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerPlugin)

dockerBaseImage := "nexus.prod.makeorg.tech/repository/docker-dev/java:8"
dockerRepository := Some("nexus.prod.makeorg.tech")
daemonUser in Docker := "user"
packageName in Docker := "repository/docker-dev/make-seed"

// dockerCmd := Seq("-Dconfig.file=conf/application.conf")

publishLocal := {
  (packageBin in Universal).value
  (publishLocal in Docker).value
  publishLocal.value
}

publish := {
  (packageBin in Universal).value
  (publish in Docker).value
  publish.value
}
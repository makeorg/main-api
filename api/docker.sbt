enablePlugins(UniversalPlugin)
enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerPlugin)

dockerBaseImage := "nexus.prod.makeorg.tech/repository/docker-dev/java:8"
dockerExposedPorts := Seq(9000)
dockerRepository := Some("nexus.prod.makeorg.tech")
daemonUser in Docker := "user"
packageName in Docker := "repository/docker-dev/make-api"

dockerCmd := Seq("-Dconfig.resource=default-application.conf")

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
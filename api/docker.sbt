enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerPlugin)

dockerBaseImage := "nexus.prod.makeorg.tech/repository/docker-dev/java:8"
dockerExposedPorts := Seq(9000)
dockerRepository := Some("nexus.prod.makeorg.tech")
daemonUser in Docker := "user"
packageName in Docker := "repository/docker-dev/make-api"

dockerCmd := Seq("-Dconfig.resource=default-application.conf")
import com.typesafe.sbt.packager.docker.Cmd

enablePlugins(UniversalPlugin)
enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerPlugin)

dockerBaseImage := "makeorg/centos-java:latest"
// Open 4k for jmx and 9k for http
dockerExposedPorts := Seq(4000, 9000)
dockerRepository := Some("nexus.prod.makeorg.tech")
daemonUser in Docker := "user"
packageName in Docker := "repository/docker-dev/make-api"

dockerCommands += Cmd("HEALTHCHECK", "CMD curl --fail http://localhost:9000/version || exit 1")

dockerCmd := Seq(
  "-Dfile.encoding=UTF-8",
  "-Dlog4j.configurationFile=conf/log4j2.yaml",
  "-Dconfig.file=conf/application.conf",
  "-Dcom.sun.management.jmxremote",
  "-Dcom.sun.management.jmxremote.ssl=false",
  "-Dcom.sun.management.jmxremote.authenticate=false",
  "-Dcom.sun.management.jmxremote.port=4000",
  "-Dcom.sun.management.rmi.jmxremote.port=4000",
  "-J-javaagent:/opt/docker/lib/org.aspectj.aspectjweaver-" + Dependencies.aspectJVersion + ".jar"
)

publishLocal := {
  (packageBin in Universal).value
  (publishLocal in Docker).value
}

publish := {
  (packageBin in Universal).value
  (publish in Docker).value
}
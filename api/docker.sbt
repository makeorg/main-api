import com.typesafe.sbt.packager.docker.Cmd

enablePlugins(UniversalPlugin)
enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerPlugin)

//dockerBaseImage := "makeorg/centos-java:latest"
dockerBaseImage := "openjdk:8-jre-alpine3.7"
// Open 4k for jmx and 9k for http
dockerExposedPorts := Seq(4000, 9000)
dockerRepository := Some("nexus.prod.makeorg.tech")
daemonUser in Docker := "make"
packageName in Docker := "make-api"

dockerCommands += Cmd("HEALTHCHECK", "CMD curl --fail http://localhost:9000/version || exit 1")
dockerCommands := {
  val originalCommands = dockerCommands.value
  originalCommands.take(2) ++
    Seq(Cmd("RUN", "apk update && apk add curl && adduser make -D")) ++
    originalCommands.drop(2)
}

dockerCmd := Seq(
  "-Dfile.encoding=UTF-8",
  "-Dlog4j.configurationFile=conf/log4j2.yaml",
  "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager",
  "-J-javaagent:/opt/docker/lib/org.aspectj.aspectjweaver-" + Dependencies.aspectJVersion + ".jar",
  "-J-Xms4G",
  "-J-Xmx4G",
  "-J-XX:+UseG1GC",
  "-J-XX:MaxGCPauseMillis=100",
  "-J-XX:MaxMetaspaceSize=1G",
  "-J-XX:MetaspaceSize=1G"
)

publishLocal := {
  (packageBin in Universal).value
  (publishLocal in Docker).value
}

publish := {
  (packageBin in Universal).value
  (publish in Docker).value
}
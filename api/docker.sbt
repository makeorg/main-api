/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

import com.typesafe.sbt.packager.docker.Cmd

enablePlugins(UniversalPlugin)
enablePlugins(JavaServerAppPackaging)
enablePlugins(DockerPlugin)

dockerBaseImage := "makeorg/centos-java:latest"
// Open 4k for jmx and 9k for http
dockerExposedPorts := Seq(4000, 9000)
dockerRepository := Some("nexus.prod.makeorg.tech")

Docker / daemonUser := "core-api"
Docker / daemonUserUid := Some("300")

Docker / daemonGroup := "apps"
Docker / daemonGroupGid := Some("200")

packageName in Docker := "make-api"

dockerCommands += Cmd(
  "HEALTHCHECK", "CMD curl --fail http://localhost:9000/version -H 'x-make-app-name: infra' || exit 1"
)

dockerCmd := Seq(
  "-Dfile.encoding=UTF-8",
  "-Dlog4j.configurationFile=conf/log4j2.yaml",
  "-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager",
  "-J-javaagent:/opt/docker/lib/org.aspectj.aspectjweaver-" + Dependencies.aspectJVersion + ".jar",
  "-J-Xms8G",
  "-J-Xmx8G",
  "-J-XX:+UseG1GC",
  "-J-XX:MaxGCPauseMillis=100",
  "-J-XX:MaxMetaspaceSize=1G",
  "-J-XX:MetaspaceSize=1G",
  "-J-XX:+PrintGCDetails",
  "-J-XX:+PrintGCDateStamps",
  "-J-Xloggc:/var/run/gc/%t-gc.log",
  "-J-XX:GCLogFileSize=5M",
  "-J-XX:+PrintGCCause",
  "-J-XX:+UseGCLogFileRotation"
)

publishLocal := {
  (packageBin in Universal).value
  (publishLocal in Docker).value
}

publish := {
  (packageBin in Universal).value
  (publish in Docker).value
}
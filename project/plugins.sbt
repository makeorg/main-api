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

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager"    % "1.7.4")
addSbtPlugin("com.eed3si9n"     % "sbt-buildinfo"          % "0.9.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-git"                % "1.0.0")
addSbtPlugin("io.kamon"         % "sbt-kanela-runner"      % "2.0.6")
addSbtPlugin("org.scalastyle"   %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"           % "2.4.2")
addSbtPlugin("org.scoverage"    % "sbt-scoverage"          % "1.6.1")
addSbtPlugin("com.codacy"       % "sbt-codacy-coverage"    % "3.0.3")
addSbtPlugin("org.make"         % "git-hooks-plugin"       % "1.0.5")
addSbtPlugin("org.make"         % "sbt-swift-plugin"       % "1.0.4")
addSbtPlugin("org.wartremover"  %% "sbt-wartremover"       % "2.4.10")

classpathTypes += "maven-plugin"

addSbtPlugin("com.typesafe.sbt"  % "sbt-native-packager"    % "1.3.2")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"          % "0.7.0")
addSbtPlugin("com.typesafe.sbt"  % "sbt-git"                % "0.9.3")
addSbtPlugin("com.lightbend.sbt" % "sbt-aspectj"            % "0.11.0")
addSbtPlugin("org.scalastyle"    %% "scalastyle-sbt-plugin" % "1.0.0")
addSbtPlugin("com.geirsson"      % "sbt-scalafmt"           % "1.2.0")
addSbtPlugin("org.scoverage"     % "sbt-scoverage"          % "1.5.1")
addSbtPlugin("org.make"          % "git-hooks-plugin"       % "1.0.4")
addSbtPlugin("io.gatling"        % "gatling-sbt"            % "2.2.2")

classpathTypes += "maven-plugin"

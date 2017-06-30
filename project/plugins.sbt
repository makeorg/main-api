addSbtPlugin("com.typesafe.sbt"  % "sbt-native-packager"    % "1.2.0-M9")
addSbtPlugin("com.github.gseitz" % "sbt-release"            % "1.0.4")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"          % "0.7.0")
addSbtPlugin("com.typesafe.sbt"  % "sbt-git"                % "0.9.2")
addSbtPlugin("com.typesafe.sbt"  % "sbt-aspectj"            % "0.10.6")
addSbtPlugin("org.scalastyle"    %% "scalastyle-sbt-plugin" % "0.8.0")
addSbtPlugin("com.geirsson"      % "sbt-scalafmt"           % "0.6.8")
addSbtPlugin("org.scoverage"     % "sbt-scoverage"          % "1.5.0")
addSbtPlugin("org.make"          % "git-hooks-plugin"       % "1.0.0")

resolvers += Resolver.url("bintray-flaroche-sbt-plugins", url("http://dl.bintray.com/flaroche/make-sbt-plugins/"))(
  Resolver.ivyStylePatterns
)

name := "make-fixtures"

libraryDependencies ++= Seq(
  Dependencies.gatling,
  Dependencies.gatlingHighcharts,
  Dependencies.postgresql,
  Dependencies.scalike
)

enablePlugins(GatlingPlugin)

addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17")

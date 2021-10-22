import sbt.{Compile, Configuration, LocalProject, Project}
import sbt.Keys.{fullClasspath, unmanagedClasspath}

object Syntax {

  implicit class ScopedDependsOn(val self: Project) extends AnyVal {
    def dependsOn(name: String, configuration: Configuration): Project =
      self.settings(configuration / unmanagedClasspath ++= (LocalProject(name) / Compile / fullClasspath).value)
  }

}

import sbt.{TaskKey, taskKey}

object Tasks {

  lazy val compileScalastyle: TaskKey[Unit] = taskKey[Unit]("compileScalastyle")
  lazy val testScalastyle: TaskKey[Unit] = taskKey[Unit]("testScalastyle")


}
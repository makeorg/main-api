import sbt.{settingKey, taskKey, SettingKey, TaskKey}

object Tasks {

  lazy val compileScalastyle: TaskKey[Unit] = taskKey[Unit]("compileScalastyle")
  lazy val testScalastyle: TaskKey[Unit] = taskKey[Unit]("testScalastyle")
  lazy val imageName: SettingKey[String] = settingKey[String]("imageName")

}

package org.make.seed

import akka.actor.ActorSystem
import java.util.concurrent.Semaphore

object Seed extends App {

  val system = ActorSystem("make-api")

  new Semaphore(0).acquire()

}

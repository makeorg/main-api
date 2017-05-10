package org.make.api

import akka.actor.{Actor, DeadLetter, Props}
import com.typesafe.scalalogging.StrictLogging

class DeadLettersListenerActor extends Actor with StrictLogging {

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[DeadLetter])
  }

  override def receive: Receive = {
    case DeadLetter(msg, from, to) =>
      logger.info("[DEADLETTERS] [{}] -> [{}]. Message: {}", from.toString, to.toString, msg.toString)
  }
}

object DeadLettersListenerActor {
  val props = Props(new DeadLettersListenerActor)
  val name = "dead-letters-logger"
}
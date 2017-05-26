package org.make.api.technical

import akka.actor.{Actor, ActorLogging, DeadLetter, Props}

class DeadLettersListenerActor extends Actor with ActorLogging {

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[DeadLetter])
  }

  override def receive: Receive = {
    case DeadLetter(msg, from, to) =>
      log.info(
        "[DEADLETTERS] [{}] -> [{}]. Message: {}",
        from.toString,
        to.toString,
        msg.toString
      )
  }
}

object DeadLettersListenerActor {
  val props = Props(new DeadLettersListenerActor)
  val name = "dead-letters-log"
}

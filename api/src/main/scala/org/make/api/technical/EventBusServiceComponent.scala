package org.make.api.technical

import akka.actor.ActorSystem

trait EventBusServiceComponent {

  def eventBusService: EventBusService

  class EventBusService(actorSystem: ActorSystem) {
    def publish(event: AnyRef): Unit = {
      actorSystem.eventStream.publish(event)
    }
  }

}

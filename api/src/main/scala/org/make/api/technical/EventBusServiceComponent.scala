package org.make.api.technical

import akka.actor.Actor
import org.make.api.ActorSystemComponent

trait EventBusServiceComponent {
  def eventBusService: EventBusService
}

trait EventBusService {
  def publish(event: AnyRef): Unit
}

trait DefaultEventBusServiceComponent extends EventBusServiceComponent {
  self: ActorSystemComponent =>

  override lazy val eventBusService = new EventBusService {
    def publish(event: AnyRef): Unit = {
      actorSystem.eventStream.publish(event)
    }
  }

}

trait ActorEventBusServiceComponent extends EventBusServiceComponent {
  actor: Actor =>

  override lazy val eventBusService = new EventBusService {
    def publish(event: AnyRef): Unit = {
      actor.context.system.eventStream.publish(event)
    }
  }
}

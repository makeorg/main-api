package org.make.api.idea

import java.time.ZonedDateTime

import org.make.core.idea.{Idea, IdeaId}
import org.make.core.{DateHelper, EventWrapper}
import shapeless.{:+:, CNil, Coproduct}


sealed trait IdeaEvent {
  def ideaId: IdeaId
  def eventDate: ZonedDateTime
}

object IdeaEvent {

  type AnyIdeaEvent =
      IdeaCreatedEvent :+:
      IdeaUpdatedEvent :+:
      CNil

  final case class IdeaEventWrapper(version: Int,
                                    id: String,
                                    date: ZonedDateTime,
                                    eventType: String,
                                    event: AnyIdeaEvent)
      extends EventWrapper

  object IdeaEventWrapper {
    def wrapEvent(event: IdeaEvent): AnyIdeaEvent =
      event match {
        case e: IdeaCreatedEvent => Coproduct[AnyIdeaEvent](e)
        case e: IdeaUpdatedEvent => Coproduct[AnyIdeaEvent](e)
      }
  }

  final case class IdeaCreatedEvent(override val ideaId: IdeaId,
                                    override val eventDate: ZonedDateTime = DateHelper.now())
    extends IdeaEvent

  object IdeaCreatedEvent {
    def apply(idea: Idea): IdeaCreatedEvent = {
      IdeaCreatedEvent(ideaId = idea.ideaId)
    }

    val version: Int = 1
  }

  final case class IdeaUpdatedEvent(override val ideaId: IdeaId,
                                    override val eventDate: ZonedDateTime = DateHelper.now())
    extends IdeaEvent

  object IdeaUpdatedEvent{
    def apply(idea: Idea): IdeaUpdatedEvent = {
      IdeaUpdatedEvent(ideaId = idea.ideaId)
    }

    val version: Int = 1
  }
}
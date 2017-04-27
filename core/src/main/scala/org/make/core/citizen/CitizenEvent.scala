package org.make.core.citizen

import java.time.{LocalDate, ZonedDateTime}

import shapeless.{:+:, CNil, Coproduct}
import org.make.core.EventWrapper

object CitizenEvent {

  type AnyCitizenEvent = CitizenRegistered :+: CitizenViewed :+: CNil

  case class CitizenEventWrapper(version: Int, id: String, date: ZonedDateTime, eventType: String, event: AnyCitizenEvent)
    extends EventWrapper

  object CitizenEventWrapper {
    def wrapEvent(event: CitizenEvent): AnyCitizenEvent = event match {
      case e: CitizenRegistered => Coproduct[AnyCitizenEvent](e)
      case e: CitizenViewed => Coproduct[AnyCitizenEvent](e)
    }
  }

  sealed trait CitizenEvent {
    def id: CitizenId
  }

  case class CitizenRegistered(
                                id: CitizenId,
                                email: String,
                                dateOfBirth: LocalDate,
                                firstName: String,
                                lastName: String
                              ) extends CitizenEvent

  case class CitizenViewed(
                          id: CitizenId
                          ) extends CitizenEvent

}
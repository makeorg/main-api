package org.make.core.user

import java.time.{LocalDate, ZonedDateTime}

import org.make.core.{EventWrapper, MakeSerializable}
import shapeless.{:+:, CNil, Coproduct}

sealed trait UserEvent extends MakeSerializable {
  def id: UserId
}

object UserEvent {

  type AnyUserEvent = UserRegistered :+: UserViewed :+: CNil

  case class UserEventWrapper(version: Int,
                              id: String,
                              date: ZonedDateTime,
                              eventType: String,
                              event: AnyUserEvent)
      extends EventWrapper

  object UserEventWrapper {
    def wrapEvent(event: UserEvent): AnyUserEvent = event match {
      case e: UserRegistered => Coproduct[AnyUserEvent](e)
      case e: UserViewed     => Coproduct[AnyUserEvent](e)
    }
  }

  case class UserRegistered(id: UserId,
                            email: String,
                            dateOfBirth: LocalDate,
                            firstName: String,
                            lastName: String)
      extends UserEvent

  case class UserViewed(id: UserId) extends UserEvent

}

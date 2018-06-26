/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.idea

import java.time.ZonedDateTime

import org.make.core.idea.{Idea, IdeaId}
import org.make.core.{DateHelper, EventWrapper, MakeSerializable}
import shapeless.{:+:, CNil, Coproduct}

sealed trait IdeaEvent {
  def ideaId: IdeaId
  def eventDate: ZonedDateTime
  def version(): Int
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
      extends IdeaEvent {

    def version(): Int = MakeSerializable.V1
  }

  object IdeaCreatedEvent {
    def apply(idea: Idea): IdeaCreatedEvent = {
      IdeaCreatedEvent(ideaId = idea.ideaId)
    }
  }

  final case class IdeaUpdatedEvent(override val ideaId: IdeaId,
                                    override val eventDate: ZonedDateTime = DateHelper.now())
      extends IdeaEvent {
    def version(): Int = MakeSerializable.V1
  }

  object IdeaUpdatedEvent {
    def apply(idea: Idea): IdeaUpdatedEvent = {
      IdeaUpdatedEvent(ideaId = idea.ideaId)
    }
  }
}

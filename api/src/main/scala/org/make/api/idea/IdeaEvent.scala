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
import com.sksamuel.avro4s._
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.{AvroSerializers, DateHelper, EventWrapper, MakeSerializable}

sealed trait IdeaEvent {
  def ideaId: IdeaId
  def eventDate: ZonedDateTime
  def version(): Int
}

object IdeaEvent {
  val defaultDate: ZonedDateTime = ZonedDateTime.parse("2017-11-01T09:00:00Z")
}

final case class IdeaEventWrapper(version: Int, id: String, date: ZonedDateTime, eventType: String, event: IdeaEvent)
    extends EventWrapper[IdeaEvent]

object IdeaEventWrapper extends AvroSerializers {
  lazy val schemaFor: SchemaFor[IdeaEventWrapper] = SchemaFor.gen[IdeaEventWrapper]
  implicit lazy val avroDecoder: Decoder[IdeaEventWrapper] = Decoder.gen[IdeaEventWrapper]
  implicit lazy val avroEncoder: Encoder[IdeaEventWrapper] = Encoder.gen[IdeaEventWrapper]
  lazy val recordFormat: RecordFormat[IdeaEventWrapper] =
    RecordFormat[IdeaEventWrapper](schemaFor.schema(DefaultFieldMapper))
}

@AvroSortPriority(2)
final case class IdeaCreatedEvent(
  override val ideaId: IdeaId,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = IdeaEvent.defaultDate
) extends IdeaEvent {

  def version(): Int = MakeSerializable.V1
}

object IdeaCreatedEvent {
  def apply(idea: Idea): IdeaCreatedEvent = {
    IdeaCreatedEvent(ideaId = idea.ideaId, eventDate = DateHelper.now())
  }
}

@AvroSortPriority(1)
final case class IdeaUpdatedEvent(
  override val ideaId: IdeaId,
  @AvroDefault("2017-11-01T09:00Z") override val eventDate: ZonedDateTime = IdeaEvent.defaultDate
) extends IdeaEvent {
  def version(): Int = MakeSerializable.V1
}

object IdeaUpdatedEvent {
  def apply(idea: Idea): IdeaUpdatedEvent = {
    IdeaUpdatedEvent(ideaId = idea.ideaId, eventDate = DateHelper.now())
  }
}

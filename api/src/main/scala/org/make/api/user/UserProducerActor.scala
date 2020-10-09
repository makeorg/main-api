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

package org.make.api.user

import akka.actor.Props
import com.sksamuel.avro4s.{RecordFormat, SchemaFor}
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}
import org.make.api.userhistory.{UserEvent, UserEventWrapper}

class UserProducerActor extends BasicProducerActor[UserEventWrapper, UserEvent] {
  override protected lazy val eventClass: Class[UserEvent] = classOf[UserEvent]
  override protected lazy val format: RecordFormat[UserEventWrapper] = UserEventWrapper.recordFormat
  override protected lazy val schema: SchemaFor[UserEventWrapper] = UserEventWrapper.schemaFor
  override val kafkaTopic: String = kafkaConfiguration.topics(UserProducerActor.topicKey)

  override protected def convert(event: UserEvent): UserEventWrapper = {
    UserEventWrapper(
      version = event.version(),
      id = event.userId.value,
      date = event.eventDate,
      eventType = event.getClass.getSimpleName,
      event = event
    )
  }
}

object UserProducerActor extends ProducerActorCompanion {
  val props: Props = Props[UserProducerActor]()
  override val name: String = "kafka-user-event-writer"
  override val topicKey: String = "users"
}

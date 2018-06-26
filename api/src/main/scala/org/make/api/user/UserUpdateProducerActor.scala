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
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical.{BasicProducerActor, ProducerActorCompanion}
import org.make.api.user.UserUpdateEvent.UserUpdateEventWrapper

class UserUpdateProducerActor extends BasicProducerActor[UserUpdateEventWrapper, UserUpdateEvent] with StrictLogging {
  override protected lazy val eventClass: Class[UserUpdateEvent] = classOf[UserUpdateEvent]
  override protected lazy val format: RecordFormat[UserUpdateEventWrapper] = RecordFormat[UserUpdateEventWrapper]
  override protected lazy val schema: SchemaFor[UserUpdateEventWrapper] = SchemaFor[UserUpdateEventWrapper]
  override val kafkaTopic: String = kafkaConfiguration.topics(UserUpdateProducerActor.topicKey)

  override protected def convert(event: UserUpdateEvent): UserUpdateEventWrapper = {
    logger.debug(s"Produce UserUpdateEvent: ${event.toString}")
    val identifier: String = (event.userId, event.email) match {
      case (Some(userId), _) => userId.value
      case (_, Some(email))  => email
      case _ =>
        log.warning("User event has been sent without email or userId")
        "invalid"
    }

    UserUpdateEventWrapper(
      id = identifier,
      version = event.version(),
      date = event.eventDate,
      eventType = event.getClass.getSimpleName,
      event = UserUpdateEventWrapper.wrapEvent(event)
    )
  }
}

object UserUpdateProducerActor extends ProducerActorCompanion {
  val props: Props = Props[UserUpdateProducerActor]
  override val name: String = "kafka-user-update-event-writer"
  override val topicKey: String = "users-update"
}

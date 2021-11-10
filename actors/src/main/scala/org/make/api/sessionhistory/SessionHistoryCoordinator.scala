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

package org.make.api.sessionhistory

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import org.make.api.extensions.MakeSettings
import org.make.api.technical.ShardingNoEnvelopeMessageExtractor
import org.make.api.userhistory.UserHistoryCommand
import org.make.core.technical.IdGenerator

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object SessionHistoryCoordinator {

  val name: String = "session-history-coordinator"

  val TypeKey: EntityTypeKey[SessionHistoryCommand] =
    EntityTypeKey[SessionHistoryCommand]("session-history")

  val Key: ServiceKey[SessionHistoryCommand] = ServiceKey(name)

  def apply(
    system: ActorSystem[_],
    userHistoryCoordinator: ActorRef[UserHistoryCommand],
    idGenerator: IdGenerator,
    settings: MakeSettings,
    lockDuration: FiniteDuration = 7.seconds
  ): ActorRef[SessionHistoryCommand] = {
    ClusterSharding(system).init(
      Entity(TypeKey)(_ => SessionHistoryActor(userHistoryCoordinator, lockDuration, idGenerator, settings))
        .withMessageExtractor(ShardingNoEnvelopeMessageExtractor[SessionHistoryCommand](numberOfShards = 128))
    )
  }
}

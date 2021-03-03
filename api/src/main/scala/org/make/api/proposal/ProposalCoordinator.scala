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

package org.make.api.proposal

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.receptionist.ServiceKey
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import org.make.api.sessionhistory.SessionHistoryCoordinatorService
import org.make.api.technical.ShardingNoEnvelopeMessageExtractor
import org.make.core.technical.IdGenerator

import scala.concurrent.duration.FiniteDuration

object ProposalCoordinator {

  val name: String = "proposal-coordinator"

  val TypeKey: EntityTypeKey[ProposalCommand] =
    EntityTypeKey[ProposalCommand]("proposal")

  val Key: ServiceKey[ProposalCommand] = ServiceKey(name)

  def apply(
    system: ActorSystem[_],
    sessionHistoryCoordinatorService: SessionHistoryCoordinatorService,
    lockDuration: FiniteDuration,
    idGenerator: IdGenerator
  ): ActorRef[ProposalCommand] = {
    ClusterSharding(system).init(
      Entity(TypeKey)(_ => ProposalActor(sessionHistoryCoordinatorService, lockDuration, idGenerator))
        .withMessageExtractor(ShardingNoEnvelopeMessageExtractor[ProposalCommand](5))
    )
  }
}

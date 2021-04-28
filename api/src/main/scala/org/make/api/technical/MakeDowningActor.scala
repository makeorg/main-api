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

package org.make.api.technical

import akka.actor.Address
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.cluster.Cluster
import org.make.constructr.coordination.Coordination

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object MakeDowningActor {
  val name: String = "MakeDowningActor"

  sealed trait Protocol
  case object AutoDown extends Protocol
  final case class MembersReceived(members: Set[Address]) extends Protocol
  final case class CoordinationError(e: Throwable) extends Protocol

  def apply(): Behavior[Protocol] = {
    Behaviors.setup { context =>
      val coordination = Coordination(context.system.name, context.system.toClassic)
      Behaviors.withTimers { timers =>
        timers.startTimerAtFixedRate(AutoDown, 10.seconds)

        Behaviors.receiveMessage {
          case AutoDown =>
            context.pipeToSelf(coordination.getNodes()) {
              case Success(nodes) => MembersReceived(nodes)
              case Failure(e)     => CoordinationError(e)
            }
            Behaviors.same
          case MembersReceived(nodes) =>
            val cluster = Cluster(context.system)
            val members = cluster.state.members
            members.foreach { member =>
              if (!nodes.contains(member.uniqueAddress.address)) {
                context.log.warn(
                  s"Downing node ${member.uniqueAddress.address.toString} since it is no longer present in coordination"
                )
                cluster.down(member.uniqueAddress.address)
              }
            }
            Behaviors.same
          case CoordinationError(e) =>
            context.log.error("Error while retrieving nodes", e)
            Behaviors.same
        }
      }
    }
  }

}

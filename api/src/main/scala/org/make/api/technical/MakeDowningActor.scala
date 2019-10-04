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

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import org.make.constructr.coordination.Coordination
import org.make.api.technical.MakeDowningActor.AutoDown

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext.Implicits.global

class MakeDowningActor extends Actor with ActorLogging {

  val constructr = Coordination(context.system.name, context.system)

  override def preStart(): Unit = {
    context.system.scheduler.schedule(10.seconds, 10.seconds, self, AutoDown)
  }

  override def receive: Receive = {
    case AutoDown =>
      val cluster = Cluster(context.system)

      val members = cluster.state.members
      val actualMembers = constructr.getNodes()

      actualMembers.onComplete {
        case Success(nodes) =>
          members.foreach { member =>
            if (!nodes.contains(member.uniqueAddress.address)) {
              log.warning(
                "Downing node {} since it is no longer present in coordination",
                member.uniqueAddress.address.toString
              )
              cluster.down(member.uniqueAddress.address)
            }
          }

        case Failure(e) =>
          log.error(e, "Error while retrieving nodes")
      }
  }
}

object MakeDowningActor {

  val name: String = "MakeDowningActor"
  val props: Props = Props[MakeDowningActor]

  case object AutoDown
}

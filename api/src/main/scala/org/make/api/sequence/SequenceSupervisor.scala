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

package org.make.api.sequence

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.make.api.MakeBackoffSupervisor
import org.make.api.technical.ShortenedNames
import org.make.api.theme.ThemeService
import org.make.core.DateHelper

class SequenceSupervisor(userHistoryCoordinator: ActorRef, themeService: ThemeService)
    extends Actor
    with ActorLogging
    with ShortenedNames
    with DefaultSequenceCoordinatorServiceComponent
    with SequenceCoordinatorComponent {

  override val sequenceCoordinator: ActorRef =
    context.watch(context.actorOf(SequenceCoordinator.props(DateHelper), SequenceCoordinator.name))

  override def preStart(): Unit = {
    context.watch(context.actorOf(SequenceProducerActor.props, SequenceProducerActor.name))

    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        SequenceUserHistoryConsumerActor.props(userHistoryCoordinator),
        SequenceUserHistoryConsumerActor.name
      )
      context.actorOf(props, name)
    }
    context.watch {
      val (props, name) = MakeBackoffSupervisor.propsAndName(
        SequenceConsumerActor
          .props(sequenceCoordinator = sequenceCoordinator, themeService = themeService),
        SequenceConsumerActor.name
      )
      context.actorOf(props, name)
    }

  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }

}

object SequenceSupervisor {

  val name: String = "sequence"
  def props(userHistoryCoordinator: ActorRef, themeService: ThemeService): Props =
    Props(new SequenceSupervisor(userHistoryCoordinator, themeService))
}

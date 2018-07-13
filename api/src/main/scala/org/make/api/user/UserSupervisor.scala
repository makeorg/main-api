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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.make.api.{kafkaDispatcher, MakeBackoffSupervisor}
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.operation.OperationService
import org.make.api.technical.{AvroSerializers, ShortenedNames}

class UserSupervisor(userService: UserService, userHistoryCoordinator: ActorRef, operationService: OperationService)
    extends Actor
    with ActorLogging
    with AvroSerializers
    with ShortenedNames
    with KafkaConfigurationExtension {

  override def preStart(): Unit = {
    context.watch(
      context
        .actorOf(UserProducerActor.props, UserProducerActor.name)
    )

    context.watch(
      context
        .actorOf(UserUpdateProducerActor.props, UserUpdateProducerActor.name)
    )

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          UserEmailConsumerActor.props(userService, operationService),
          UserEmailConsumerActor.name
        )
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          UserCrmConsumerActor.props(userService).withDispatcher(kafkaDispatcher),
          UserCrmConsumerActor.name
        )
      context.actorOf(props, name)
    }

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          UserHistoryConsumerActor.props(userHistoryCoordinator).withDispatcher(kafkaDispatcher),
          UserHistoryConsumerActor.name
        )
      context.actorOf(props, name)
    }

  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object UserSupervisor {

  val name: String = "users"
  def props(userService: UserService, userHistoryCoordinator: ActorRef, operationService: OperationService): Props =
    Props(new UserSupervisor(userService, userHistoryCoordinator, operationService))
}

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
import org.make.api.extensions.KafkaConfigurationExtension
import org.make.api.operation.{OperationOfQuestionServiceComponent, OperationServiceComponent}
import org.make.api.organisation.{
  OrganisationConsumerActor,
  OrganisationSearchEngineComponent,
  OrganisationServiceComponent
}
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationComponent
import org.make.api.technical.{AvroSerializers, ShortenedNames}
import org.make.api.user.UserSupervisor.UserSupervisorDependencies
import org.make.api.{kafkaDispatcher, MakeBackoffSupervisor}

class UserSupervisor(userHistoryCoordinator: ActorRef, dependencies: UserSupervisorDependencies)
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

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          UserEmailConsumerActor
            .props(dependencies.userService, dependencies.questionService, dependencies.operationOfQuestionService),
          UserEmailConsumerActor.name
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

    context.watch {
      val (props, name) =
        MakeBackoffSupervisor.propsAndName(
          OrganisationConsumerActor
            .props(
              dependencies.organisationService,
              dependencies.elasticsearchOrganisationAPI,
              dependencies.elasticsearchConfiguration
            )
            .withDispatcher(kafkaDispatcher),
          OrganisationConsumerActor.name
        )
      context.actorOf(props, name)
    }

  }

  override def receive: Receive = {
    case x => log.info(s"received $x")
  }
}

object UserSupervisor {

  type UserSupervisorDependencies = UserServiceComponent
    with OperationServiceComponent
    with OrganisationServiceComponent
    with OrganisationSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with QuestionServiceComponent
    with OperationOfQuestionServiceComponent

  val name: String = "users"
  def props(userHistoryCoordinator: ActorRef, dependencies: UserSupervisorDependencies): Props =
    Props(new UserSupervisor(userHistoryCoordinator, dependencies))
}

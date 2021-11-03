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

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, Props}
import org.make.api.kafka.kafkaDispatcher
import org.make.api.operation.OperationServiceComponent
import org.make.api.organisation.{
  OrganisationConsumerBehavior,
  OrganisationSearchEngineComponent,
  OrganisationServiceComponent
}
import org.make.api.technical.ActorSystemHelper.superviseWithBackoff
import org.make.api.technical.crm.SendMailPublisherServiceComponent
import org.make.api.technical.elasticsearch.ElasticsearchConfigurationComponent
import org.make.api.userhistory.UserHistoryCoordinatorServiceComponent

object UserSupervisor {

  def apply(dependencies: UserSupervisorDependencies): Behavior[Nothing] = {
    Behaviors.setup[Nothing] { context =>
      context.watch(context.spawn(UserProducerBehavior(), UserProducerBehavior.name))

      context.watch(
        context.spawn(
          superviseWithBackoff(
            UserEmailConsumerBehavior(dependencies.userService, dependencies.sendMailPublisherService)
          ),
          UserEmailConsumerBehavior.name,
          Props.empty.withDispatcherFromConfig(kafkaDispatcher)
        )
      )

      context.watch(
        context.spawn(
          superviseWithBackoff(
            UserHistoryConsumerBehavior(dependencies.userHistoryCoordinatorService, dependencies.userService)
          ),
          UserHistoryConsumerBehavior.name,
          Props.empty.withDispatcherFromConfig(kafkaDispatcher)
        )
      )

      context.watch(
        context.spawn(
          superviseWithBackoff(UserImageConsumerBehavior(dependencies.userService)),
          UserImageConsumerBehavior.name,
          Props.empty.withDispatcherFromConfig(kafkaDispatcher)
        )
      )

      context.spawn(
        superviseWithBackoff(
          OrganisationConsumerBehavior(dependencies.organisationService, dependencies.elasticsearchOrganisationAPI)
        ),
        OrganisationConsumerBehavior.name,
        Props.empty.withDispatcherFromConfig(kafkaDispatcher)
      )

      Behaviors.empty
    }
  }

  type UserSupervisorDependencies = UserServiceComponent
    with OperationServiceComponent
    with OrganisationServiceComponent
    with OrganisationSearchEngineComponent
    with ElasticsearchConfigurationComponent
    with SendMailPublisherServiceComponent
    with UserServiceComponent
    with UserHistoryCoordinatorServiceComponent

  val name: String = "users"
}

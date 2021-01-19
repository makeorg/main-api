/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

package org.make.api.technical.graphql

import caliban.schema.GenericSchema
import caliban.wrappers.Wrappers.{printErrors, printSlowQueries}
import caliban.{GraphQL, RootResolver}
import grizzled.slf4j.Logging
import org.make.api.proposal.{ProposalServiceComponent, SortAlgorithmConfigurationComponent}
import org.make.api.technical.graphql.GraphQLProposalQuery.{GraphQLProposalQueries, ProposalSearchParams}
import org.make.api.technical.graphql.GraphQLRuntimeComponent.{EnvType, RuntimeType}
import org.make.api.technical.graphql.GraphQLUtils._
import org.make.core.RequestContext
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.{FiberRef, Has, Task, ZIO}

trait GraphQLRuntime {
  def graphQLService: GraphQL[RuntimeType]
}

trait GraphQLRuntimeComponent {
  def graphQLRuntime: GraphQLRuntime
}

object GraphQLRuntimeComponent {
  type EnvType = FiberRef[RequestContext]
  type RuntimeType = Console with Clock with Has[EnvType]
}

trait DefaultGraphQLRuntimeComponent
    extends GraphQLRuntimeComponent
    with GraphQLAuthorServiceComponent
    with GraphQLIdeaServiceComponent
    with GraphQLOrganisationServiceComponent
    with GraphQLQuestionServiceComponent
    with GraphQLTagServiceComponent {
  self: ProposalServiceComponent with SortAlgorithmConfigurationComponent =>

  override def graphQLRuntime: GraphQLRuntime = new DefaultGraphQLRuntime

  class DefaultGraphQLRuntime extends GraphQLRuntime with Logging {
    override lazy val graphQLService: GraphQL[RuntimeType] = {
      val queries = GraphQLProposalQueries(search = { parameters =>
        ZIO.accessM[RuntimeType](_.get[EnvType].get).flatMap { rc =>
          searchProposals(rc, parameters)
        }
      })

      val runtimeSchema: GenericSchema[RuntimeType] = new GenericSchema[RuntimeType] {}
      import runtimeSchema._

      GraphQL.graphQL(RootResolver(AllQueries(queries))) @@ printErrors @@ printSlowQueries(500.millis) @@ debugPrint
    }

    private def searchProposals(
      requestContext: RequestContext,
      params: ProposalSearchParams
    ): Task[Seq[GraphQLProposal]] = {
      Task.fromFuture(
        implicit ec =>
          proposalService
            .searchForUser(
              requestContext.userId,
              params.toSearchQuery(requestContext, sortAlgorithmConfiguration),
              requestContext
            )
            .map {
              _.results.map { proposal =>
                GraphQLProposal.fromProposalResponse(proposal)(
                  authorDataSource,
                  questionDataSource,
                  organisationDataSource,
                  tagDataSource,
                  ideaDataSource
                )
              }
            }
      )
    }
  }

}

final case class AllQueries(proposal: GraphQLProposalQueries)

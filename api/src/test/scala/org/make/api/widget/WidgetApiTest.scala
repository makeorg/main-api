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

package org.make.api.widget

import java.time.ZonedDateTime

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.{ProposalSearchEngine, ProposalSearchEngineComponent}
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.proposal.indexed.{Author, IndexedProposal, IndexedScores, ProposalsSearchResult}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.mockito.{ArgumentMatchers, Mockito}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class WidgetApiTest
  extends MakeApiTestBase
  with WidgetApi
  with MakeDataHandlerComponent
  with IdGeneratorComponent
  with MakeSettingsComponent
  with ProposalSearchEngineComponent {

  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]

  val routes: Route = sealRoute(widgetRoutes)

  feature("get widget sequence of an operation") {
    scenario("Get proposal of an operation successfully") {
      Mockito
        .when(elasticsearchProposalAPI.searchProposals(ArgumentMatchers.eq(
          SearchQuery(
            filters = Some(SearchFilters(
              operation = Some(OperationSearchFilter(OperationId("foo-operation-id")))
            ))
          )
        )))
        .thenReturn(Future.successful(ProposalsSearchResult(
          total = 0,
          results = Seq.empty
        )))

      Get("/widget/operations/foo-operation-id/start-sequence") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[ProposalsSearchResult]
        result.total should be(0)
        result.results should be(Seq.empty)
      }
    }

    scenario("Get proposal of an operation filtred by tag successfully") {
      Mockito
        .when(elasticsearchProposalAPI.searchProposals(ArgumentMatchers.eq(
          SearchQuery(
            filters = Some(SearchFilters(
              operation = Some(OperationSearchFilter(OperationId("foo-operation-id"))),
              tags = Some(TagsSearchFilter(Seq(TagId("foo-tag-id"), TagId("bar-tag-id"))))
            ))
          )
        )))
        .thenReturn(Future.successful(ProposalsSearchResult(
          total = 1,
          results = Seq(IndexedProposal(
            id = ProposalId("foo-proposal-id"),
            userId = UserId("foo-user-id"),
            content = "il faut proposer sur le widget",
            slug = "il-faut-proposer-sur-le-widget",
            status = ProposalStatus.Accepted,
            createdAt = ZonedDateTime.now,
            updatedAt = Some(ZonedDateTime.now),
            votes = Seq.empty,
            scores = IndexedScores.empty,
            context = None,
            trending = None,
            labels = Seq.empty,
            author = Author(
              firstName = Some("Foo Foo"),
              organisationName = None,
              postalCode = None,
              age = None,
              avatarUrl = None
            ),
            organisations = Seq.empty,
            themeId = None,
            tags = Seq.empty,
            ideaId = None,
            operationId = None,
            country = Country("FR"),
            language = Language("fr")
          ))
        )))

      Get("/widget/operations/foo-operation-id/start-sequence?tagsIds=foo-tag-id,bar-tag-id") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[ProposalsSearchResult]
        result.total should be(1)
        result.results.head shouldBe a[IndexedProposal]
      }
    }
  }
}

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

package org.make.api.views

import java.time.ZonedDateTime

import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import cats.data.NonEmptyList
import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.{OperationOfQuestionService, OperationOfQuestionServiceComponent}
import org.make.api.organisation.{OrganisationService, OrganisationServiceComponent, OrganisationsSearchResultResponse}
import org.make.api.proposal.{ProposalService, ProposalServiceComponent, ProposalsResultSeededResponse}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.RequestContext
import org.make.core.operation.{
  OperationKind,
  OperationOfQuestionSearchFilters,
  OperationOfQuestionSearchQuery,
  QuestionContentSearchFilter,
  OperationKindsSearchFilter => OpKindsSearchFilter
}
import org.make.core.operation.indexed.{IndexedOperationOfQuestion, OperationOfQuestionSearchResult}
import org.make.core.proposal.{ContentSearchFilter, OperationKindsSearchFilter, SearchFilters, SearchQuery}
import org.make.core.reference.Country
import org.make.core.user.{OrganisationNameSearchFilter, OrganisationSearchFilters, OrganisationSearchQuery}
import org.make.core.user.indexed.OrganisationSearchResult

import scala.concurrent.Future

class ViewApiTest
    extends MakeApiTestBase
    with DefaultViewApiComponent
    with MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ProposalServiceComponent
    with OperationOfQuestionServiceComponent
    with HomeViewServiceComponent
    with OrganisationServiceComponent {

  override val homeViewService: HomeViewService = mock[HomeViewService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val organisationService: OrganisationService = mock[OrganisationService]

  val routes: Route = sealRoute(viewApi.routes)

  Feature("search") {

    Scenario("without content") {
      Get("/views/search") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    Scenario("valid content") {
      when(
        proposalService.searchForUser(
          eqTo(None),
          eqTo(
            SearchQuery(
              filters = Some(
                SearchFilters(
                  content = Some(ContentSearchFilter("toto")),
                  operationKinds = Some(OperationKindsSearchFilter(OperationKind.publicKinds)),
                  country = None,
                  language = None
                )
              ),
              limit = None
            )
          ),
          any[RequestContext]
        )
      ).thenReturn(Future.successful(ProposalsResultSeededResponse(total = 2, results = Seq.empty, seed = None)))
      when(
        operationOfQuestionService.search(
          eqTo(
            OperationOfQuestionSearchQuery(
              filters = Some(
                OperationOfQuestionSearchFilters(
                  question = Some(QuestionContentSearchFilter("toto", fuzzy = Some(Fuzziness.Auto))),
                  operationKinds = Some(OpKindsSearchFilter(OperationKind.publicKinds))
                )
              ),
              limit = None
            )
          )
        )
      ).thenReturn(Future.successful(OperationOfQuestionSearchResult(total = 2, results = Seq.empty)))
      when(
        organisationService.searchWithQuery(
          eqTo(
            OrganisationSearchQuery(
              filters =
                Some(OrganisationSearchFilters(organisationName = Some(OrganisationNameSearchFilter(text = "toto")))),
              limit = None
            )
          )
        )
      ).thenReturn(Future.successful(OrganisationSearchResult(total = 1, results = Seq.empty)))

      Get("/views/search?content=toto").withHeaders(Accept(MediaTypes.`application/json`)) ~> routes ~> check {

        status should be(StatusCodes.OK)
        val search: SearchViewResponse = entityAs[SearchViewResponse]
        search.proposals.total shouldBe 2
        search.questions.total shouldBe 2
        search.organisations.total shouldBe 1
      }
    }

    Scenario("valid content but empty result") {
      when(
        proposalService.searchForUser(
          eqTo(None),
          eqTo(
            SearchQuery(
              filters = Some(
                SearchFilters(
                  content = Some(ContentSearchFilter("lownoresults")),
                  operationKinds = Some(OperationKindsSearchFilter(OperationKind.publicKinds)),
                  country = None,
                  language = None
                )
              ),
              limit = None
            )
          ),
          any[RequestContext]
        )
      ).thenReturn(Future.successful(ProposalsResultSeededResponse(total = 0, results = Seq.empty, seed = None)))
      when(
        operationOfQuestionService.search(
          eqTo(
            OperationOfQuestionSearchQuery(
              filters = Some(
                OperationOfQuestionSearchFilters(
                  question = Some(QuestionContentSearchFilter("lownoresults", fuzzy = Some(Fuzziness.Auto))),
                  operationKinds = Some(OpKindsSearchFilter(OperationKind.publicKinds))
                )
              ),
              limit = None
            )
          )
        )
      ).thenReturn(Future.successful(OperationOfQuestionSearchResult(total = 0, results = Seq.empty)))
      when(
        organisationService.searchWithQuery(
          eqTo(
            OrganisationSearchQuery(
              filters = Some(
                OrganisationSearchFilters(organisationName = Some(OrganisationNameSearchFilter(text = "lownoresults")))
              ),
              limit = None
            )
          )
        )
      ).thenReturn(Future.successful(OrganisationSearchResult(total = 0, results = Seq.empty)))

      Get("/views/search?content=LOWnoResults").withHeaders(Accept(MediaTypes.`application/json`)) ~> routes ~> check {

        status should be(StatusCodes.OK)
        val search: SearchViewResponse = entityAs[SearchViewResponse]
        search.proposals.total shouldBe 0
        search.questions.total shouldBe 0
//  Edit this following part when further implemented
        search.organisations shouldBe OrganisationsSearchResultResponse.empty
      }
    }
  }

  Feature("available countries") {
    Scenario("it works") {
      val ooqs = {
        def gen(startDate: ZonedDateTime, endDate: ZonedDateTime, first: Country, others: Country*) = {
          val q = question(id = idGenerator.nextQuestionId(), countries = NonEmptyList.of(first, others: _*))
          val o = simpleOperation(id = idGenerator.nextOperationId())
          val ooq = operationOfQuestion(q.questionId, o.operationId, startDate = startDate, endDate = endDate)
          IndexedOperationOfQuestion.createFromOperationOfQuestion(ooq, o, q)
        }
        Seq(
          gen(startDate = ZonedDateTime.now.minusDays(1), endDate = ZonedDateTime.now.plusDays(1), Country("FR")),
          gen(
            startDate = ZonedDateTime.now.minusDays(2),
            endDate = ZonedDateTime.now.minusDays(1),
            Country("DE"),
            Country("FR")
          )
        )
      }

      when(operationOfQuestionService.count(any[OperationOfQuestionSearchQuery]))
        .thenReturn(Future.successful(ooqs.size))
      when(operationOfQuestionService.search(any))
        .thenReturn(Future.successful(OperationOfQuestionSearchResult(ooqs.size, ooqs)))

      Get("/views/countries") ~> routes ~> check {
        entityAs[Seq[AvailableCountry]] should contain theSameElementsAs Seq(
          AvailableCountry("FR", true),
          AvailableCountry("DE", false)
        )
      }
    }
  }
}

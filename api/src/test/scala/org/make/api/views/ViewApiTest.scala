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

import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import org.make.api.MakeApiTestBase
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.{
  CurrentOperationService,
  CurrentOperationServiceComponent,
  FeaturedOperationService,
  FeaturedOperationServiceComponent,
  OperationOfQuestionService,
  OperationOfQuestionServiceComponent,
  OperationService,
  OperationServiceComponent
}
import org.make.api.organisation.{OrganisationService, OrganisationServiceComponent}
import org.make.api.proposal.{
  ProposalSearchEngine,
  ProposalSearchEngineComponent,
  ProposalService,
  ProposalServiceComponent,
  ProposalsResultSeededResponse
}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.IdGeneratorComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.RequestContext
import org.make.core.operation.{
  OperationKind,
  OperationOfQuestionSearchFilters,
  OperationOfQuestionSearchQuery,
  QuestionContentSearchFilter
}
import org.make.core.operation
import org.make.core.operation.indexed.OperationOfQuestionSearchResult
import org.make.core.proposal.{ContentSearchFilter, OperationKindsSearchFilter, SearchFilters, SearchQuery}
import org.make.core.user.{OrganisationNameSearchFilter, OrganisationSearchFilters, OrganisationSearchQuery}
import org.make.core.user.indexed.OrganisationSearchResult
import org.mockito.{ArgumentMatchers, Mockito}

import scala.concurrent.Future

class ViewApiTest
    extends MakeApiTestBase
    with DefaultViewApiComponent
    with MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with QuestionServiceComponent
    with FeaturedOperationServiceComponent
    with CurrentOperationServiceComponent
    with ProposalServiceComponent
    with OperationOfQuestionServiceComponent
    with OperationServiceComponent
    with ProposalSearchEngineComponent
    with HomeViewServiceComponent
    with OrganisationServiceComponent {

  override val homeViewService: HomeViewService = mock[HomeViewService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val questionService: QuestionService = mock[QuestionService]
  override val featuredOperationService: FeaturedOperationService = mock[FeaturedOperationService]
  override val currentOperationService: CurrentOperationService = mock[CurrentOperationService]
  override val operationOfQuestionService: OperationOfQuestionService = mock[OperationOfQuestionService]
  override val operationService: OperationService = mock[OperationService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]
  override val organisationService: OrganisationService = mock[OrganisationService]

  val routes: Route = sealRoute(viewApi.routes)

  feature("search") {

    scenario("without content") {
      Get("/views/search") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("valid content") {
      Mockito
        .when(
          proposalService.searchForUser(
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(
              SearchQuery(
                filters = Some(
                  SearchFilters(
                    content = Some(ContentSearchFilter("toto", fuzzy = Some(Fuzziness.Auto))),
                    operationKinds = Some(
                      OperationKindsSearchFilter(
                        Seq(
                          OperationKind.GreatCause,
                          OperationKind.PublicConsultation,
                          OperationKind.BusinessConsultation
                        )
                      )
                    ),
                    country = None,
                    language = None
                  )
                ),
                limit = None
              )
            ),
            ArgumentMatchers.any[RequestContext]
          )
        )
        .thenReturn(Future.successful(ProposalsResultSeededResponse(total = 2, results = Seq.empty, seed = None)))
      Mockito
        .when(
          operationOfQuestionService.search(
            ArgumentMatchers.eq(
              OperationOfQuestionSearchQuery(
                filters = Some(
                  OperationOfQuestionSearchFilters(
                    question = Some(QuestionContentSearchFilter("toto", fuzzy = Some(Fuzziness.Auto))),
                    operationKinds = Some(
                      operation.OperationKindsSearchFilter(
                        Seq(
                          OperationKind.GreatCause,
                          OperationKind.PublicConsultation,
                          OperationKind.BusinessConsultation
                        )
                      )
                    )
                  )
                ),
                limit = None
              )
            )
          )
        )
        .thenReturn(Future.successful(OperationOfQuestionSearchResult(total = 2, results = Seq.empty)))
      Mockito
        .when(
          organisationService.searchWithQuery(
            ArgumentMatchers.eq(
              OrganisationSearchQuery(
                filters = Some(
                  OrganisationSearchFilters(
                    organisationName = Some(OrganisationNameSearchFilter(text = "toto", fuzzy = Some(Fuzziness.Auto)))
                  )
                ),
                limit = None
              )
            )
          )
        )
        .thenReturn(Future.successful(OrganisationSearchResult(total = 1, results = Seq.empty)))

      Get("/views/search?content=toto").withHeaders(Accept(MediaTypes.`application/json`)) ~> routes ~> check {

        status should be(StatusCodes.OK)
        val search: SearchViewResponse = entityAs[SearchViewResponse]
        search.proposals.total shouldBe 2
        search.questions.total shouldBe 2
        search.organisations.total shouldBe 1
      }
    }

    scenario("valid content but empty result") {
      Mockito
        .when(
          proposalService.searchForUser(
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(
              SearchQuery(
                filters = Some(
                  SearchFilters(
                    content = Some(ContentSearchFilter("lownoresults", fuzzy = Some(Fuzziness.Auto))),
                    operationKinds = Some(
                      OperationKindsSearchFilter(
                        Seq(
                          OperationKind.GreatCause,
                          OperationKind.PublicConsultation,
                          OperationKind.BusinessConsultation
                        )
                      )
                    ),
                    country = None,
                    language = None
                  )
                ),
                limit = None
              )
            ),
            ArgumentMatchers.any[RequestContext]
          )
        )
        .thenReturn(Future.successful(ProposalsResultSeededResponse(total = 0, results = Seq.empty, seed = None)))
      Mockito
        .when(
          operationOfQuestionService.search(
            ArgumentMatchers.eq(
              OperationOfQuestionSearchQuery(
                filters = Some(
                  OperationOfQuestionSearchFilters(
                    question = Some(QuestionContentSearchFilter("lownoresults", fuzzy = Some(Fuzziness.Auto))),
                    operationKinds = Some(
                      operation.OperationKindsSearchFilter(
                        Seq(
                          OperationKind.GreatCause,
                          OperationKind.PublicConsultation,
                          OperationKind.BusinessConsultation
                        )
                      )
                    )
                  )
                ),
                limit = None
              )
            )
          )
        )
        .thenReturn(Future.successful(OperationOfQuestionSearchResult(total = 0, results = Seq.empty)))
      Mockito
        .when(
          organisationService.searchWithQuery(
            ArgumentMatchers.eq(
              OrganisationSearchQuery(
                filters = Some(
                  OrganisationSearchFilters(
                    organisationName =
                      Some(OrganisationNameSearchFilter(text = "lownoresults", fuzzy = Some(Fuzziness.Auto)))
                  )
                ),
                limit = None
              )
            )
          )
        )
        .thenReturn(Future.successful(OrganisationSearchResult(total = 0, results = Seq.empty)))

      Get("/views/search?content=LOWnoResults").withHeaders(Accept(MediaTypes.`application/json`)) ~> routes ~> check {

        status should be(StatusCodes.OK)
        val search: SearchViewResponse = entityAs[SearchViewResponse]
        search.proposals.total shouldBe 0
        search.questions.total shouldBe 0
//  Edit this following part when further implemented
        search.organisations shouldBe OrganisationSearchResult.empty
      }
    }
  }
}

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

import akka.http.scaladsl.server.{Directives, Route}
import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation._
import org.make.api.proposal.{ProposalSearchEngineComponent, ProposalServiceComponent}
import org.make.api.question.{QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.idea.{CountrySearchFilter, LanguageSearchFilter}
import org.make.core.operation.{OperationId, OperationKind, OperationOfQuestion}
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.indexed.OrganisationSearchResult
import org.make.core.{HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

@Api(value = "Home view")
@Path(value = "/views")
trait ViewApi extends Directives {

  @ApiOperation(value = "get-home-view", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[HomeViewResponse]))
  )
  @Path(value = "/home")
  def homeView: Route

  @ApiOperation(value = "get-search-view", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SearchViewResponse]))
  )
  @Path(value = "/search")
  def searchView: Route

  def routes: Route = homeView ~ searchView
}

trait ViewApiComponent {
  def viewApi: ViewApi
}

trait DefaultViewApiComponent
    extends ViewApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors
    with StrictLogging {
  this: MakeDataHandlerComponent
    with SessionHistoryCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with QuestionServiceComponent
    with FeaturedOperationServiceComponent
    with CurrentOperationServiceComponent
    with ProposalServiceComponent
    with OperationOfQuestionServiceComponent
    with OperationServiceComponent
    with ProposalSearchEngineComponent =>

  override val viewApi: ViewApi = new DefaultViewApi

  class DefaultViewApi extends ViewApi {

    override def homeView: Route = {
      get {
        path("views" / "home") {
          makeOperation("GetHomeView") { requestContext =>
            optionalMakeOAuth2 { auth: Option[AuthInfo[UserRights]] =>
              provideAsync(
                operationService
                  .findSimple(operationKinds = Some(Seq(OperationKind.GreatCause, OperationKind.PublicConsultation)))
              ) { publicOperations =>
                provideAsync(featuredOperationService.getAll) { featured =>
                  provideAsync(
                    operationService.findSimple(operationKinds = Some(Seq(OperationKind.BusinessConsultation)))
                  ) { business =>
                    provideAsync(currentOperationService.getAll) { current =>
                      provideAsync(elasticsearchProposalAPI.countProposalsByQuestion(Some(current.map(_.questionId)))) {
                        proposalNumberByQuestion =>
                          val maybeQuestionIds: Option[Seq[QuestionId]] =
                            Option(featured.flatMap(_.questionId) ++ current.map(_.questionId)).filter(_.nonEmpty)
                          val maybeOperationIds: Option[Seq[OperationId]] =
                            Option(business.map(_.operationId)).filter(_.nonEmpty)
                          provideAsync(
                            questionService.searchQuestion(
                              SearchQuestionRequest(
                                language = requestContext.language,
                                country = requestContext.country,
                                maybeQuestionIds = maybeQuestionIds,
                              )
                            )
                          ) { questions =>
                            provideAsync(
                              questionService.searchQuestion(
                                SearchQuestionRequest(
                                  language = requestContext.language,
                                  country = requestContext.country,
                                  maybeOperationIds = maybeOperationIds
                                )
                              )
                            ) { questionsBusiness =>
                              provideAsync(
                                operationOfQuestionService.find(
                                  request = SearchOperationsOfQuestions(
                                    operationIds = Option(publicOperations.map(_.operationId)).filter(_.nonEmpty),
                                    openAt = Some(ZonedDateTime.now())
                                  )
                                )
                              ) { publicQuestions =>
                                provideAsync(
                                  proposalService.searchForUser(
                                    userId = auth.map(_.user.userId),
                                    query = SearchQuery(
                                      limit = Some(2),
                                      sortAlgorithm = Some(PopularAlgorithm),
                                      filters = Some(
                                        SearchFilters(
                                          language = requestContext.language.map(LanguageSearchFilter.apply),
                                          country = requestContext.country.map(CountrySearchFilter.apply),
                                          question = Some(QuestionSearchFilter(publicQuestions.map(_.questionId)))
                                        )
                                      )
                                    ),
                                    requestContext = requestContext
                                  )
                                ) { popularProposals =>
                                  provideAsync(
                                    proposalService.searchForUser(
                                      userId = auth.map(_.user.userId),
                                      query = SearchQuery(
                                        limit = Some(2),
                                        sortAlgorithm = Some(ControversyAlgorithm),
                                        filters = Some(
                                          SearchFilters(
                                            language = requestContext.language.map(LanguageSearchFilter.apply),
                                            country = requestContext.country.map(CountrySearchFilter.apply),
                                            question = Some(QuestionSearchFilter(publicQuestions.map(_.questionId)))
                                          )
                                        )
                                      ),
                                      requestContext = requestContext
                                    )
                                  ) { controverseProposals =>
                                    provideAsync(
                                      operationOfQuestionService
                                        .find(
                                          request = SearchOperationsOfQuestions(
                                            questionIds = Some(questionsBusiness.map(_.questionId))
                                          )
                                        )
                                    ) { businessDetails =>
                                      val featuredConsultations = featured
                                        .sortBy(_.slot)
                                        .map(
                                          feat =>
                                            FeaturedConsultationResponse(
                                              feat,
                                              feat.questionId
                                                .flatMap(qId => questions.find(_.questionId == qId).map(_.slug))
                                          )
                                        )
                                      val businessConsultations = business.flatMap { bus =>
                                        def question(details: OperationOfQuestion): Option[Question] =
                                          questionsBusiness.find(_.questionId == details.questionId)
                                        businessDetails
                                          .filter(_.operationId == bus.operationId)
                                          .map(
                                            details =>
                                              BusinessConsultationResponse(
                                                theme = BusinessConsultationThemeResponse(
                                                  details.theme.gradientStart,
                                                  details.theme.gradientEnd
                                                ),
                                                startDate = details.startDate,
                                                endDate = details.endDate,
                                                slug = question(details).map(_.slug),
                                                aboutUrl = details.aboutUrl,
                                                question = question(details).map(_.question).getOrElse("")
                                            )
                                          )
                                      }
                                      val currentConsultations = current.map { cur =>
                                        {
                                          val maybeQuestion: Option[Question] =
                                            questions.find(_.questionId == cur.questionId)
                                          val maybeQuestionDetails: Option[OperationOfQuestion] =
                                            maybeQuestion.flatMap { question =>
                                              businessDetails.find(_.questionId == question.questionId)
                                            }

                                          CurrentConsultationResponse(
                                            current = cur,
                                            slug = maybeQuestion.map(_.slug),
                                            startDate = maybeQuestionDetails.flatMap(_.startDate),
                                            endDate = maybeQuestionDetails.flatMap(_.endDate),
                                            proposalsNumber = proposalNumberByQuestion.getOrElse(cur.questionId, 0)
                                          )
                                        }
                                      }
                                      complete(
                                        HomeViewResponse(
                                          popularProposals = popularProposals.results,
                                          controverseProposals = controverseProposals.results,
                                          businessConsultations = businessConsultations,
                                          featuredConsultations = featuredConsultations,
                                          currentConsultations = currentConsultations
                                        )
                                      )
                                    }
                                  }
                                }
                              }
                            }
                          }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def searchView: Route = {
      get {
        path("views" / "search") {
          makeOperation("GetSearchView") { requestContext =>
            optionalMakeOAuth2 { auth: Option[AuthInfo[UserRights]] =>
              parameters(
                (
                  'content,
                  'proposalLimit.as[Int].?,
                  'questionLimit.as[Int].?,
                  'organisationLimit.as[Int].?,
                  'country.as[Country].?,
                  'language.as[Language].?
                )
              ) { (content, proposalLimit, _ /*questionLimit*/, _ /*organisationLimit*/, country, language) =>
                val query = SearchQuery(
                  filters = Some(
                    SearchFilters(
                      content = Some(ContentSearchFilter(content.toLowerCase, fuzzy = Some(Fuzziness.Auto))),
                      operationKinds = Some(
                        OperationKindsSearchFilter(
                          Seq(
                            OperationKind.GreatCause,
                            OperationKind.PublicConsultation,
                            OperationKind.BusinessConsultation
                          )
                        )
                      ),
                      country = country.map(CountrySearchFilter.apply),
                      language = language.map(LanguageSearchFilter.apply)
                    )
                  ),
                  limit = proposalLimit
                )
                provideAsync(proposalService.searchForUser(auth.map(_.user.userId), query, requestContext)) {
                  proposals =>
                    complete(
                      SearchViewResponse(
                        proposals = proposals,
                        questions = Seq.empty,
                        organisations = OrganisationSearchResult.empty
                      )
                    )
                }
              }
            }
          }
        }
      }

    }
  }

}

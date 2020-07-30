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

import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation._
import org.make.api.organisation.{OrganisationServiceComponent, OrganisationsSearchResultResponse}
import org.make.api.proposal.ProposalServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.operation.{
  OperationKind,
  OperationOfQuestionSearchFilters,
  OperationOfQuestionSearchQuery,
  QuestionContentSearchFilter
}
import org.make.core.proposal.{CountrySearchFilter, LanguageSearchFilter, SearchQuery}
import org.make.core.reference.{Country, Language}
import org.make.core.user.{OrganisationNameSearchFilter, OrganisationSearchFilters, OrganisationSearchQuery}
import org.make.core.{operation, proposal, user, BusinessConfig, HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global

@Api(value = "Home view")
@Path(value = "/views")
trait ViewApi extends Directives {

  @ApiOperation(value = "get-home-page-view", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "country", paramType = "path", dataType = "string", example = "FR"),
      new ApiImplicitParam(name = "language", paramType = "path", dataType = "string", example = "fr")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[HomePageViewResponse]))
  )
  @Path(value = "/home-page/{country}/{language}")
  def homePageView: Route

  @ApiOperation(value = "get-search-view", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "content", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "proposalLimit", paramType = "query", dataType = "int", example = "10"),
      new ApiImplicitParam(name = "questionLimit", paramType = "query", dataType = "int", example = "10"),
      new ApiImplicitParam(name = "organisationLimit", paramType = "query", dataType = "int", example = "10"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string", example = "FR"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string", example = "fr")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SearchViewResponse]))
  )
  @Path(value = "/search")
  def searchView: Route

  def routes: Route = homePageView ~ searchView
}

trait ViewApiComponent {
  def viewApi: ViewApi
}

trait DefaultViewApiComponent
    extends ViewApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors
    with StrictLogging {
  this: SessionHistoryCoordinatorServiceComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ProposalServiceComponent
    with OperationOfQuestionServiceComponent
    with HomeViewServiceComponent
    with OrganisationServiceComponent =>

  override lazy val viewApi: ViewApi = new DefaultViewApi

  class DefaultViewApi extends ViewApi {

    private val country: PathMatcher1[Country] = Segment.map(Country.apply)
    private val language: PathMatcher1[Language] = Segment.map(Language.apply)

    override def homePageView: Route = {
      get {
        path("views" / "home-page" / country / language) { (country, language) =>
          makeOperation("GetHomePageView") { _ =>
            provideAsync(
              homeViewService.getHomePageViewResponse(
                country = BusinessConfig.validateCountry(country),
                language = BusinessConfig.validateLanguage(country, language)
              )
            )(complete(_))
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
                  "content",
                  "proposalLimit".as[Int].?,
                  "questionLimit".as[Int].?,
                  "organisationLimit".as[Int].?,
                  "country".as[Country].?,
                  "language".as[Language].?
                )
              ) { (content, proposalLimit, questionLimit, organisationLimit, country, language) =>
                val proposalQuery = SearchQuery(
                  filters = Some(
                    proposal.SearchFilters(
                      content = Some(proposal.ContentSearchFilter(content.toLowerCase)),
                      operationKinds = Some(
                        proposal.OperationKindsSearchFilter(
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
                  limit = proposalLimit,
                  language = requestContext.locale.map(_.language)
                )
                val questionQuery = OperationOfQuestionSearchQuery(
                  filters = Some(
                    OperationOfQuestionSearchFilters(
                      question = Some(QuestionContentSearchFilter(content.toLowerCase, fuzzy = Some(Fuzziness.Auto))),
                      operationKinds = Some(
                        operation.OperationKindsSearchFilter(
                          Seq(
                            OperationKind.GreatCause,
                            OperationKind.PublicConsultation,
                            OperationKind.BusinessConsultation
                          )
                        )
                      ),
                      country = country.map(operation.CountrySearchFilter.apply),
                      language = language.map(operation.LanguageSearchFilter.apply)
                    )
                  ),
                  limit = questionLimit
                )
                val organisationQuery = OrganisationSearchQuery(
                  filters = Some(
                    OrganisationSearchFilters(
                      organisationName = Some(OrganisationNameSearchFilter(content.toLowerCase)),
                      country = country.map(user.CountrySearchFilter.apply),
                      language = language.map(user.LanguageSearchFilter.apply)
                    )
                  ),
                  limit = organisationLimit
                )
                val futureResults = for {
                  proposals <- proposalService
                    .searchForUser(auth.map(_.user.userId), proposalQuery, requestContext)
                  questions     <- operationOfQuestionService.search(questionQuery)
                  organisations <- organisationService.searchWithQuery(organisationQuery)
                } yield (
                  proposals,
                  questions,
                  OrganisationsSearchResultResponse.fromOrganisationSearchResult(organisations)
                )
                provideAsync(futureResults) {
                  case (proposals, questions, organisations) =>
                    complete(
                      SearchViewResponse(proposals = proposals, questions = questions, organisations = organisations)
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

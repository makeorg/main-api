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
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, ObjectEncoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation._
import org.make.api.proposal.{ProposalResponse, ProposalSearchEngineComponent, ProposalServiceComponent}
import org.make.api.question.{QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.idea.{CountrySearchFilter, LanguageSearchFilter}
import org.make.core.operation._
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.question.{Question, QuestionId}
import org.make.core.{CirceFormatters, HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(value = "Home view")
@Path(value = "/views/home")
trait ViewApi extends Directives {

  @ApiOperation(value = "get-home-view", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[HomeViewResponse]))
  )
  @Path(value = "/")
  def homeView: Route

  def routes: Route = homeView
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
                                operationOfQuestionService.search(
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
                                        .search(request = SearchOperationsOfQuestions(operationIds = maybeOperationIds))
                                    ) { businessDetails =>
                                      val featuredConsultations = featured.map(
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
                                                title = details.operationTitle
                                            )
                                          )
                                      }
                                      val currentConsultations = current.map(
                                        cur =>
                                          CurrentConsultationResponse(
                                            current = cur,
                                            slug = questions.find(_.questionId == cur.questionId).map(_.slug),
                                            proposalsNumber = proposalNumberByQuestion.getOrElse(cur.questionId, 0)
                                        )
                                      )
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

  }

}

final case class HomeViewResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  popularProposals: Seq[ProposalResponse],
  controverseProposals: Seq[ProposalResponse],
  businessConsultations: Seq[BusinessConsultationResponse],
  featuredConsultations: Seq[FeaturedConsultationResponse],
  currentConsultations: Seq[CurrentConsultationResponse]
)

object HomeViewResponse {
  implicit val encoder: Encoder[HomeViewResponse] = deriveEncoder[HomeViewResponse]
}

final case class BusinessConsultationThemeResponse(gradientStart: String, gradientEnd: String)

object BusinessConsultationThemeResponse {
  implicit val encoder: ObjectEncoder[BusinessConsultationThemeResponse] =
    deriveEncoder[BusinessConsultationThemeResponse]
  implicit val decoder: Decoder[BusinessConsultationThemeResponse] = deriveDecoder[BusinessConsultationThemeResponse]
}

final case class BusinessConsultationResponse(theme: BusinessConsultationThemeResponse,
                                              startDate: Option[ZonedDateTime],
                                              endDate: Option[ZonedDateTime],
                                              slug: Option[String],
                                              aboutUrl: Option[String],
                                              title: String)

object BusinessConsultationResponse extends CirceFormatters {
  implicit val encoder: ObjectEncoder[BusinessConsultationResponse] = deriveEncoder[BusinessConsultationResponse]
  implicit val decoder: Decoder[BusinessConsultationResponse] = deriveDecoder[BusinessConsultationResponse]
}

final case class FeaturedConsultationResponse(questionId: Option[QuestionId],
                                              questionSlug: Option[String],
                                              title: String,
                                              description: Option[String],
                                              landscapePicture: String,
                                              portraitPicture: String,
                                              altPicture: String,
                                              label: String,
                                              buttonLabel: String,
                                              internalLink: Option[String],
                                              externalLink: Option[String])

object FeaturedConsultationResponse {
  implicit val encoder: ObjectEncoder[FeaturedConsultationResponse] = deriveEncoder[FeaturedConsultationResponse]
  implicit val decoder: Decoder[FeaturedConsultationResponse] = deriveDecoder[FeaturedConsultationResponse]

  def apply(featured: FeaturedOperation, slug: Option[String]): FeaturedConsultationResponse =
    FeaturedConsultationResponse(
      questionId = featured.questionId,
      questionSlug = slug,
      title = featured.title,
      description = featured.description,
      landscapePicture = featured.landscapePicture,
      portraitPicture = featured.portraitPicture,
      altPicture = featured.altPicture,
      label = featured.label,
      buttonLabel = featured.buttonLabel,
      internalLink = featured.internalLink,
      externalLink = featured.externalLink
    )
}

final case class CurrentConsultationResponse(questionId: Option[QuestionId],
                                             questionSlug: Option[String],
                                             picture: String,
                                             altPicture: String,
                                             description: String,
                                             linkLabel: String,
                                             internalLink: Option[String],
                                             externalLink: Option[String],
                                             proposalsNumber: Long)

object CurrentConsultationResponse {
  implicit val encoder: ObjectEncoder[CurrentConsultationResponse] = deriveEncoder[CurrentConsultationResponse]
  implicit val decoder: Decoder[CurrentConsultationResponse] = deriveDecoder[CurrentConsultationResponse]

  def apply(current: CurrentOperation, slug: Option[String], proposalsNumber: Long): CurrentConsultationResponse =
    CurrentConsultationResponse(
      questionId = Some(current.questionId),
      questionSlug = slug,
      picture = current.picture,
      altPicture = current.altPicture,
      description = current.description,
      linkLabel = current.linkLabel,
      internalLink = current.internalLink,
      externalLink = current.externalLink,
      proposalsNumber = proposalsNumber,
    )
}

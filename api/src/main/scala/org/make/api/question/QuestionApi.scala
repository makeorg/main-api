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

package org.make.api.question

import java.time.ZonedDateTime

import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.feature.{ActiveFeatureServiceComponent, FeatureServiceComponent}
import org.make.api.operation.{
  OperationOfQuestionServiceComponent,
  OperationServiceComponent,
  PersistentOperationOfQuestionServiceComponent
}
import org.make.api.organisation.OrganisationSearchEngineComponent
import org.make.api.partner.PartnerServiceComponent
import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.sequence.{SequenceResult, SequenceServiceComponent}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{EndpointType, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.common.indexed.Order
import org.make.core.operation._
import org.make.core.operation.indexed.{OperationOfQuestionElasticsearchFieldNames, OperationOfQuestionSearchResult}
import org.make.core.partner.PartnerKind
import org.make.core.proposal.ProposalId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.{CountrySearchFilter => _, DescriptionSearchFilter => _, LanguageSearchFilter => _, _}
import org.make.core.{HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field
import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait QuestionApiComponent {
  def questionApi: QuestionApi
}

@Api(value = "Questions")
@Path(value = "/questions")
trait QuestionApi extends Directives {

  @ApiOperation(value = "get-question-details", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "questionSlugOrQuestionId", paramType = "path", dataType = "string"))
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[QuestionDetailsResponse]))
  )
  @Path(value = "/{questionSlugOrQuestionId}/details")
  def questionDetails: Route

  @ApiOperation(value = "start-sequence-by-question", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "include", paramType = "query", dataType = "string", allowMultiple = true)
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceResult])))
  @Path(value = "/{questionId}/start-sequence")
  def startSequenceByQuestionId: Route

  @ApiOperation(value = "get-search-question", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "questionContent", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "description", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "startDate", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "endDate", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "operationKinds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationOfQuestionSearchResult]))
  )
  @Path(value = "/search")
  def searchQuestions: Route

  @ApiOperation(value = "get-question-popular-tags", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[PopularTagResponse]]))
  )
  @Path(value = "/{questionId}/popular-tags")
  def getPopularTags: Route

  @ApiOperation(value = "get-question-partners", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "sortAlgorithm", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "partnerKind", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[PopularTagResponse]]))
  )
  @Path(value = "/{questionId}/partners")
  def getPartners: Route

  def routes: Route = questionDetails ~ startSequenceByQuestionId ~ searchQuestions ~ getPopularTags ~ getPartners
}

trait DefaultQuestionApiComponent
    extends QuestionApiComponent
    with SequenceServiceComponent
    with PersistentOperationOfQuestionServiceComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: QuestionServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with OperationServiceComponent
    with OperationOfQuestionServiceComponent
    with PartnerServiceComponent
    with FeatureServiceComponent
    with ActiveFeatureServiceComponent
    with ProposalSearchEngineComponent
    with TagServiceComponent
    with OrganisationSearchEngineComponent =>

  override lazy val questionApi: QuestionApi = new DefaultQuestionApi

  class DefaultQuestionApi extends QuestionApi {

    private val questionId: PathMatcher1[QuestionId] = Segment.map(id => QuestionId(id))
    private val questionSlugOrQuestionId: PathMatcher1[String] = Segment

    // TODO: remove the public access once authent is handled in server side
    override def questionDetails: Route = get {
      path("questions" / questionSlugOrQuestionId / "details") { questionSlugOrQuestionId =>
        makeOperation("GetQuestionDetails", EndpointType.Public) { _ =>
          provideAsyncOrNotFound {
            questionService.getQuestionByQuestionIdValueOrSlug(questionSlugOrQuestionId)
          } { question =>
            provideAsyncOrNotFound(operationOfQuestionService.findByQuestionId(question.questionId)) {
              operationOfQuestion =>
                provideAsyncOrNotFound(operationService.findOne(operationOfQuestion.operationId)) { operation =>
                  provideAsync(
                    partnerService.find(
                      questionId = Some(question.questionId),
                      organisationId = None,
                      start = 0,
                      end = None,
                      sort = Some("weight"),
                      order = Some("DESC"),
                      partnerKind = None
                    )
                  ) { partners =>
                    provideAsync(findQuestionsOfOperation(operationOfQuestion.operationId)) { questions =>
                      provideAsync(findActiveFeatureSlugsByQuestionId(question.questionId)) { activeFeatureSlugs =>
                        complete(
                          QuestionDetailsResponse(
                            question,
                            operation,
                            operationOfQuestion,
                            partners,
                            questions,
                            activeFeatureSlugs
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

    private def findQuestionsOfOperation(operationId: OperationId): Future[Seq[QuestionOfOperationResponse]] = {
      operationOfQuestionService.findByOperationId(operationId).flatMap { operationOfQuestions =>
        questionService.getQuestions(operationOfQuestions.map(_.questionId)).map { questions =>
          val questionMap: Map[QuestionId, Question] = questions.map { question =>
            question.questionId -> question
          }.toMap
          operationOfQuestions.map { operationOfQuestion =>
            val question = questionMap(operationOfQuestion.questionId)
            QuestionOfOperationResponse(
              questionId = question.questionId,
              questionSlug = question.slug,
              question = question.question,
              operationTitle = operationOfQuestion.operationTitle,
              country = question.country,
              language = question.language,
              startDate = operationOfQuestion.startDate,
              endDate = operationOfQuestion.endDate,
              theme = QuestionThemeResponse.fromQuestionTheme(operationOfQuestion.theme)
            )
          }.sortBy(_.questionSlug)
        }
      }
    }

    private def findActiveFeatureSlugsByQuestionId(questionId: QuestionId): Future[Seq[String]] = {
      activeFeatureService.find(maybeQuestionId = Some(questionId)).flatMap { activeFeatures =>
        featureService.findByFeatureIds(activeFeatures.map(_.featureId)).map(_.map(_.slug))
      }
    }

    override def startSequenceByQuestionId: Route = get {
      path("questions" / questionId / "start-sequence") { questionId =>
        makeOperation("StartSequenceByQuestionId") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            parameters(Symbol("include").*) { includes =>
              provideAsyncOrNotFound(persistentOperationOfQuestionService.getById(questionId)) { operationOfQuestion =>
                provideAsyncOrNotFound(
                  sequenceService
                    .startNewSequence(
                      maybeUserId = userAuth.map(_.user.userId),
                      sequenceId = operationOfQuestion.landingSequenceId,
                      includedProposals = includes.toSeq.map(ProposalId(_)),
                      tagsIds = None,
                      requestContext = requestContext
                    )
                ) { sequences =>
                  complete(sequences)
                }
              }
            }
          }
        }
      }
    }

    override def searchQuestions: Route = get {
      path("questions" / "search") {
        makeOperation("GetQuestionDetails") { _ =>
          parameters(
            (
              Symbol("questionIds").as[immutable.Seq[QuestionId]].?,
              Symbol("questionContent").?,
              Symbol("description").?,
              Symbol("startDate").as[ZonedDateTime].?,
              Symbol("endDate").as[ZonedDateTime].?,
              Symbol("operationKinds").as[immutable.Seq[OperationKind]].?,
              Symbol("language").as[Language].?,
              Symbol("country").as[Country].?,
              Symbol("limit").as[Int].?,
              Symbol("skip").as[Int].?,
              Symbol("sort").?,
              Symbol("order").?
            )
          ) {
            (questionIds: Option[Seq[QuestionId]],
             questionContent: Option[String],
             description: Option[String],
             startDate: Option[ZonedDateTime],
             endDate: Option[ZonedDateTime],
             operationKinds: Option[Seq[OperationKind]],
             language: Option[Language],
             country: Option[Country],
             limit: Option[Int],
             skip: Option[Int],
             sort: Option[String],
             order: Option[String]) =>
              Validation.validate(
                Seq(
                  sort.map { sortValue =>
                    val choices =
                      Seq(
                        OperationOfQuestionElasticsearchFieldNames.question,
                        OperationOfQuestionElasticsearchFieldNames.startDate,
                        OperationOfQuestionElasticsearchFieldNames.endDate,
                        OperationOfQuestionElasticsearchFieldNames.description,
                        OperationOfQuestionElasticsearchFieldNames.country,
                        OperationOfQuestionElasticsearchFieldNames.language,
                        OperationOfQuestionElasticsearchFieldNames.operationKind
                      )
                    Validation.validChoices(
                      fieldName = "sort",
                      message = Some(
                        s"Invalid sort. Got $sortValue but expected one of: ${choices.mkString("\"", "\", \"", "\"")}"
                      ),
                      Seq(sortValue),
                      choices
                    )
                  },
                  order.map { orderValue =>
                    Validation.validChoices(
                      fieldName = "order",
                      message = Some(s"Invalid order. Expected one of: ${Order.orders.keys}"),
                      Seq(orderValue),
                      Order.orders.keys.toSeq
                    )
                  }
                ).flatten: _*
              )
              val filters: Option[OperationOfQuestionSearchFilters] = Some(
                OperationOfQuestionSearchFilters(
                  questionIds = questionIds.map(QuestionIdsSearchFilter.apply),
                  question = questionContent.map(QuestionContentSearchFilter(_, Some(Fuzziness.Auto))),
                  description = description.map(DescriptionSearchFilter.apply),
                  country = country.map(CountrySearchFilter.apply),
                  language = language.map(LanguageSearchFilter.apply),
                  startDate = startDate.map(StartDateSearchFilter.apply),
                  endDate = endDate.map(EndDateSearchFilter.apply),
                  operationKinds = operationKinds.map(OperationKindsSearchFilter.apply)
                )
              )
              val searchQuery: OperationOfQuestionSearchQuery =
                OperationOfQuestionSearchQuery(
                  filters = filters,
                  limit = limit,
                  skip = skip,
                  sort = sort,
                  order = order
                )
              provideAsync(operationOfQuestionService.search(searchQuery)) { searchResult =>
                complete(searchResult)
              }
          }
        }

      }
    }

    override def getPopularTags: Route = get {
      path("questions" / questionId / "popular-tags") { questionId =>
        makeOperation("GetQuestionPopularTags") { _ =>
          parameters((Symbol("limit").as[Int].?, Symbol("skip").as[Int].?)) { (limit: Option[Int], skip: Option[Int]) =>
            provideAsyncOrNotFound(questionService.getQuestion(questionId)) { _ =>
              val size = limit.getOrElse(10) + skip.getOrElse(0)

              provideAsync(elasticsearchProposalAPI.getPopularTagsByProposal(questionId, size)) { popularTagsResponse =>
                val popularTags = popularTagsResponse.sortBy(_.proposalCount * -1).drop(skip.getOrElse(0))
                complete(popularTags)
              }
            }
          }
        }
      }
    }

    override def getPartners: Route = get {
      path("questions" / questionId / "partners") { questionId =>
        makeOperation("GetQuestionPartners") { _ =>
          parameters(
            (
              Symbol("sortAlgorithm").as[String].?,
              Symbol("partnerKind").as[PartnerKind].?,
              Symbol("limit").as[Int].?,
              Symbol("skip").as[Int].?
            )
          ) {
            (sortAlgorithm: Option[String], partnerKind: Option[PartnerKind], limit: Option[Int], skip: Option[Int]) =>
              provideAsyncOrNotFound(questionService.getQuestion(questionId)) { _ =>
                Validation.validate(Seq(sortAlgorithm.map { sortAlgo =>
                  Validation.validChoices(
                    fieldName = "sortAlgorithm",
                    message =
                      Some(s"Invalid algorithm. Expected one of: ${OrganisationAlgorithmSelector.sortAlgorithmsName}"),
                    Seq(sortAlgo),
                    OrganisationAlgorithmSelector.sortAlgorithmsName
                  )
                }).flatten: _*)

                provideAsync(
                  partnerService
                    .find(
                      start = 0,
                      end = Some(1000),
                      sort = None,
                      order = None,
                      questionId = Some(questionId),
                      organisationId = None,
                      partnerKind = partnerKind
                    )
                ) { partners =>
                  val query = OrganisationSearchQuery(
                    filters = Some(
                      OrganisationSearchFilters(
                        organisationIds = Some(OrganisationIdsSearchFilter(partners.flatMap(_.organisationId)))
                      )
                    ),
                    sortAlgorithm = OrganisationAlgorithmSelector.select(sortAlgorithm),
                    limit = limit,
                    skip = skip
                  )

                  provideAsync(elasticsearchOrganisationAPI.searchOrganisations(query)) { organisationSearchResult =>
                    complete(organisationSearchResult)
                  }
                }
              }
          }
        }
      }
    }

  }
}

final case class StartSequenceByQuestionIdRequest(include: Option[Seq[ProposalId]] = None)

object StartSequenceByQuestionIdRequest {
  implicit val decoder: Decoder[StartSequenceByQuestionIdRequest] = deriveDecoder[StartSequenceByQuestionIdRequest]
}

final case class PopularTagResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "7353ae89-0d05-4014-8aa0-1d7cb0b3aea3") tagId: TagId,
  label: String,
  proposalCount: Long
)

object PopularTagResponse {
  implicit val decoder: Decoder[PopularTagResponse] = deriveDecoder[PopularTagResponse]
  implicit val encoder: Encoder[PopularTagResponse] = deriveEncoder[PopularTagResponse]
}

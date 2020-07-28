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
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.feature.{ActiveFeatureServiceComponent, FeatureServiceComponent}
import org.make.api.idea.topIdeaComments.TopIdeaCommentServiceComponent
import org.make.api.operation.{OperationOfQuestionServiceComponent, OperationServiceComponent, ResultsLinkResponse}
import org.make.api.organisation.OrganisationsSearchResultResponse
import org.make.api.partner.PartnerServiceComponent
import org.make.api.personality.PersonalityRoleServiceComponent
import org.make.api.proposal.{ProposalSearchEngineComponent, ProposalServiceComponent, ProposalsResultResponse}
import org.make.api.sequence.{SequenceResult, SequenceServiceComponent}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{EndpointType, IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.common.indexed.Order
import org.make.core.idea.TopIdeaId
import org.make.core.operation._
import org.make.core.operation.indexed.{OperationOfQuestionElasticsearchFieldNames, OperationOfQuestionSearchResult}
import org.make.core.partner.PartnerKind
import org.make.core.personality.PersonalityRoleId
import org.make.core.proposal.ProposalId
import org.make.core.question.{Question, QuestionId, TopProposalsMode}
import org.make.core.reference.{Country, Language}
import org.make.core.user.{CountrySearchFilter => _, DescriptionSearchFilter => _, LanguageSearchFilter => _, _}
import org.make.core.{BusinessConfig, HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.ApiParamMagnetHelper._

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
      new ApiImplicitParam(name = "startDate", paramType = "query", dataType = "date"),
      new ApiImplicitParam(name = "endDate", paramType = "query", dataType = "date"),
      new ApiImplicitParam(
        name = "operationKinds",
        paramType = "query",
        dataType = "string",
        allowableValues = "GREAT_CAUSE,PUBLIC_CONSULTATION,PRIVATE_CONSULTATION,BUSINESS_CONSULTATION"
      ),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string", allowableValues = "ASC,DESC")
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
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[PopularTagResponse]]))
  )
  @Path(value = "/{questionId}/popular-tags")
  def getPopularTags: Route

  @ApiOperation(value = "get-top-proposals", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(
        name = "mode",
        paramType = "query",
        dataType = "string",
        allowableValues = "tag,idea",
        allowEmptyValue = true
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultResponse]))
  )
  @Path(value = "/{questionId}/top-proposals")
  def getTopProposals: Route

  @ApiOperation(value = "get-question-partners", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        name = "sortAlgorithm",
        paramType = "query",
        dataType = "string",
        allowableValues = "participation"
      ),
      new ApiImplicitParam(
        name = "partnerKind",
        paramType = "query",
        dataType = "string",
        allowableValues = "MEDIA,ACTION_PARTNER,FOUNDER,ACTOR"
      ),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer")
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OrganisationsSearchResultResponse]))
  )
  @Path(value = "/{questionId}/partners")
  def getPartners: Route

  @ApiOperation(value = "get-question-personalities", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "personalityRoleId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer")
    )
  )
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[QuestionPersonalityResponseWithTotal])
    )
  )
  @Path(value = "/{questionId}/personalities")
  def getPersonalities: Route

  @ApiOperation(value = "get-question-top-ideas", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "seed", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[QuestionTopIdeasResponseWithSeed]))
  )
  @Path(value = "/{questionId}/top-ideas")
  def getTopIdeas: Route

  @ApiOperation(value = "get-question-top-idea", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "topIdeaId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "seed", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[QuestionTopIdeaResponseWithSeed]))
  )
  @Path(value = "/{questionId}/top-ideas/{topIdeaId}")
  def getTopIdea: Route

  @ApiOperation(value = "list-questions", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string", example = "FR", required = true),
      new ApiImplicitParam(
        name = "language",
        paramType = "query",
        dataType = "string",
        example = "fr",
        required = true
      ),
      new ApiImplicitParam(
        name = "status",
        paramType = "query",
        dataType = "string",
        allowableValues = "Upcoming,Open,Finished"
      ),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(
        name = "sortAlgorithm",
        paramType = "query",
        dataType = "string",
        allowableValues = "participation"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[QuestionListResponse]))
  )
  @Path(value = "/")
  def listQuestions: Route

  def routes: Route =
    questionDetails ~ startSequenceByQuestionId ~ searchQuestions ~ getPopularTags ~ getTopProposals ~ getPartners ~
      getPersonalities ~ getTopIdeas ~ getTopIdea ~ listQuestions
}

trait DefaultQuestionApiComponent
    extends QuestionApiComponent
    with SequenceServiceComponent
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
    with ProposalServiceComponent
    with TopIdeaCommentServiceComponent
    with PersonalityRoleServiceComponent =>

  override lazy val questionApi: QuestionApi = new DefaultQuestionApi

  class DefaultQuestionApi extends QuestionApi {

    private val questionId: PathMatcher1[QuestionId] = Segment.map(id => QuestionId(id))
    private val topIdeaId: PathMatcher1[TopIdeaId] = Segment.map(id   => TopIdeaId(id))
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
              shortTitle = question.shortTitle,
              operationTitle = operationOfQuestion.operationTitle,
              consultationImage = operationOfQuestion.consultationImage,
              descriptionImage = operationOfQuestion.descriptionImage,
              country = question.country,
              language = question.language,
              startDate = operationOfQuestion.startDate,
              endDate = operationOfQuestion.endDate,
              theme = QuestionThemeResponse.fromQuestionTheme(operationOfQuestion.theme),
              displayResults = operationOfQuestion.resultsLink.isDefined,
              resultsLink = operationOfQuestion.resultsLink.map(ResultsLinkResponse.apply),
              aboutUrl = operationOfQuestion.aboutUrl,
              actions = operationOfQuestion.actions,
              featured = operationOfQuestion.featured,
              participantsCount = operationOfQuestion.participantsCount,
              proposalsCount = operationOfQuestion.proposalsCount
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
            parameters("include".as[Seq[ProposalId]].*) { includes =>
              provideAsyncOrNotFound(
                sequenceService
                  .startNewSequence(
                    maybeUserId = userAuth.map(_.user.userId),
                    questionId = questionId,
                    includedProposals = includes.getOrElse(Seq.empty),
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

    override def searchQuestions: Route = get {
      path("questions" / "search") {
        makeOperation("GetQuestionDetails") { _ =>
          parameters(
            (
              "questionIds".as[Seq[QuestionId]].?,
              "questionContent".?,
              "description".?,
              "startDate".as[ZonedDateTime].?,
              "endDate".as[ZonedDateTime].?,
              "operationKinds".as[Seq[OperationKind]].?,
              "language".as[Language].?,
              "country".as[Country].?,
              "limit".as[Int].?,
              "skip".as[Int].?,
              "sort".?,
              "order".?
            )
          ) {
            (
              questionIds: Option[Seq[QuestionId]],
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
              order: Option[String]
            ) =>
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
                      message = Some(s"Invalid order. Expected one of: ${Order.keys}"),
                      Seq(orderValue),
                      Order.keys
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
          parameters(("limit".as[Int].?, "skip".as[Int].?)) { (limit: Option[Int], skip: Option[Int]) =>
            provideAsyncOrNotFound(questionService.getQuestion(questionId)) { _ =>
              val offset = skip.getOrElse(0)
              val size = limit.map(_ + offset).getOrElse(Int.MaxValue)

              provideAsync(elasticsearchProposalAPI.getPopularTagsByProposal(questionId, size)) { popularTagsResponse =>
                val popularTags = popularTagsResponse.sortBy(_.proposalCount * -1).drop(offset)
                complete(popularTags)
              }
            }
          }
        }
      }
    }

    override def getTopProposals: Route = get {
      path("questions" / questionId / "top-proposals") { questionId =>
        makeOperation("GetTopProposals") { requestContext =>
          parameters(("limit".as[Int].?, "mode".as[TopProposalsMode].?)) {
            (limit: Option[Int], mode: Option[TopProposalsMode]) =>
              optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
                provideAsyncOrNotFound(questionService.getQuestion(questionId)) { _ =>
                  provideAsync(
                    proposalService.getTopProposals(
                      maybeUserId = userAuth.map(_.user.userId),
                      questionId = questionId,
                      size = limit.getOrElse(10),
                      mode = mode,
                      requestContext = requestContext
                    )
                  ) { proposalsResponse =>
                    complete(proposalsResponse)
                  }
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
            ("sortAlgorithm".as[String].?, "partnerKind".as[PartnerKind].?, "limit".as[Int].?, "skip".as[Int].?)
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
                  val organisationsIds = partners.flatMap(_.organisationId)
                  val sortAlgo = OrganisationAlgorithmSelector.select(sortAlgorithm, Some(questionId))
                  provideAsync(questionService.getPartners(questionId, organisationsIds, sortAlgo, limit, skip)) {
                    results =>
                      complete(OrganisationsSearchResultResponse.fromOrganisationSearchResult(results))
                  }
                }
              }
          }
        }
      }
    }

    override def getPersonalities: Route = {
      get {
        path("questions" / questionId / "personalities") { questionId =>
          makeOperation("GetQuestionPersonalities") { _ =>
            parameters(("personalityRole".as[String].?, "limit".as[Int].?, "skip".as[Int].?)) {
              (personalityRole: Option[String], limit: Option[Int], skip: Option[Int]) =>
                provideAsync(
                  personalityRoleService
                    .find(start = 0, end = None, sort = None, order = None, roleIds = None, name = personalityRole)
                ) { roles =>
                  val personalityRoleId: Option[PersonalityRoleId] = roles match {
                    case Seq(role) if personalityRole.nonEmpty => Some(role.personalityRoleId)
                    case _                                     => None
                  }
                  Validation.validate(
                    Validation.validateField(
                      field = "personalityRole",
                      key = "invalid_content",
                      condition = personalityRole.forall(_ => personalityRoleId.isDefined),
                      message = s"$personalityRole is not a valid personality role"
                    )
                  )
                  provideAsync(
                    questionService.getQuestionPersonalities(
                      start = skip.getOrElse(0),
                      end = limit,
                      questionId = questionId,
                      personalityRoleId = personalityRoleId
                    )
                  ) { questionPersonalities =>
                    val response = QuestionPersonalityResponseWithTotal(
                      total = questionPersonalities.size,
                      results = questionPersonalities.sortBy(_.lastName)
                    )
                    complete(response)
                  }
                }
            }
          }
        }
      }
    }

    override def getTopIdeas: Route = {
      get {
        path("questions" / questionId / "top-ideas") { questionId =>
          makeOperation("GetQuestionTopIdeas") { _ =>
            parameters(("limit".as[Int].?, "skip".as[Int].?, "seed".as[Int].?)) {
              (limit: Option[Int], skip: Option[Int], seed: Option[Int]) =>
                provideAsync(
                  questionService
                    .getTopIdeas(skip.getOrElse(0), limit, seed, questionId)
                ) { response =>
                  complete(response)
                }
            }
          }
        }
      }
    }

    override def getTopIdea: Route = {
      get {
        path("questions" / questionId / "top-ideas" / topIdeaId) { (questionId, topIdeaId) =>
          makeOperation("GetQuestionTopIdea") { _ =>
            parameters("seed".as[Int].?) { (seed: Option[Int]) =>
              provideAsyncOrNotFound(questionService.getTopIdea(topIdeaId, questionId, seed)) { topIdeaResult =>
                provideAsync(
                  topIdeaCommentService
                    .getCommentsWithPersonality(topIdeaIds = Seq(topIdeaResult.topIdea.topIdeaId))
                ) { comments =>
                  val response = QuestionTopIdeaResponseWithSeed(
                    questionTopIdea = QuestionTopIdeaWithAvatarAndCommentsResponse(
                      id = topIdeaId,
                      ideaId = topIdeaResult.topIdea.ideaId,
                      questionId = topIdeaResult.topIdea.questionId,
                      name = topIdeaResult.topIdea.name,
                      label = topIdeaResult.topIdea.label,
                      scores = topIdeaResult.topIdea.scores,
                      proposalsCount = topIdeaResult.proposalsCount,
                      avatars = topIdeaResult.avatars,
                      weight = topIdeaResult.topIdea.weight,
                      comments = comments
                    ),
                    seed = topIdeaResult.seed
                  )
                  complete(response)
                }
              }
            }
          }
        }
      }
    }

    override def listQuestions: Route = {
      get {
        path("questions") {
          makeOperation("ListQuestions") { _ =>
            parameters(
              (
                "country".as[Country],
                "language".as[Language],
                "status".as[OperationOfQuestion.Status].?,
                "limit".as[Int].?,
                "skip".as[Int].?,
                "sortAlgorithm".as[SortAlgorithm].?
              )
            ) {
              (
                country: Country,
                language: Language,
                status: Option[OperationOfQuestion.Status],
                limit: Option[Int],
                skip: Option[Int],
                sortAlgorithm: Option[SortAlgorithm]
              ) =>
                val filters = OperationOfQuestionSearchFilters(
                  country = Some(CountrySearchFilter(BusinessConfig.validateCountry(country))),
                  language = Some(LanguageSearchFilter(BusinessConfig.validateLanguage(country, language))),
                  status = status.map(StatusSearchFilter.apply)
                )
                provideAsync(
                  operationOfQuestionService.search(
                    OperationOfQuestionSearchQuery(
                      filters = Some(filters),
                      limit = limit,
                      skip = skip,
                      sortAlgorithm = sortAlgorithm
                    )
                  )
                ) { operationOfQuestions =>
                  complete(
                    QuestionListResponse(
                      results = operationOfQuestions.results.map(QuestionOfOperationResponse.apply),
                      total = operationOfQuestions.total
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

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

import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import com.sksamuel.elastic4s.requests.searches.suggestion.Fuzziness
import grizzled.slf4j.Logging
import io.swagger.annotations._
import org.make.api.feature.{ActiveFeatureServiceComponent, FeatureServiceComponent}
import org.make.api.idea.topIdeaComments.TopIdeaCommentServiceComponent
import org.make.api.keyword.KeywordServiceComponent
import org.make.api.operation.{
  OperationOfQuestionSearchEngineComponent,
  OperationOfQuestionServiceComponent,
  OperationServiceComponent,
  ResultsLinkResponse
}
import org.make.api.organisation.OrganisationsSearchResultResponse
import org.make.api.partner.PartnerServiceComponent
import org.make.api.personality.PersonalityRoleServiceComponent
import org.make.api.proposal.{
  ProposalResponse,
  ProposalSearchEngineComponent,
  ProposalServiceComponent,
  ProposalsResultResponse,
  ProposalsResultSeededResponse
}
import org.make.api.sequence.SequenceServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{EndpointType, MakeAuthenticationDirectives}
import org.make.core.Validation.validateField
import org.make.core.auth.UserRights
import org.make.core.feature.FeatureSlug
import org.make.core.feature.FeatureSlug.DisplayIntroCardWidget
import org.make.core.idea.TopIdeaId
import org.make.core.keyword.Keyword
import org.make.core.operation._
import org.make.core.operation.indexed.{OperationOfQuestionElasticsearchFieldName, OperationOfQuestionSearchResult}
import org.make.core.partner.PartnerKind
import org.make.core.personality.PersonalityRoleId
import org.make.core.proposal.{
  MinScoreLowerBoundSearchFilter,
  PopularAlgorithm,
  QuestionSearchFilter,
  SearchFilters,
  SearchQuery,
  SequencePoolSearchFilter,
  ZoneSearchFilter
}
import org.make.core.proposal.indexed.{SequencePool, Zone}
import org.make.core.question.{Question, QuestionId, TopProposalsMode}
import org.make.core.reference.{Country, Language}
import org.make.core.technical.Pagination._
import org.make.core.user.{CountrySearchFilter => _, DescriptionSearchFilter => _, LanguageSearchFilter => _, _}
import org.make.core.{HttpCodes, Order, ParameterExtractors, RequestContext, Validation}
import scalaoauth2.provider.AuthInfo

import javax.ws.rs.Path
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
        allowableValues = "GREAT_CAUSE,PRIVATE_CONSULTATION,BUSINESS_CONSULTATION"
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

  @ApiOperation(value = "featured-proposals", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "maxPartnerProposals", paramType = "query", dataType = "integer", required = true),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer", required = true),
      new ApiImplicitParam(name = "seed", paramType = "query", dataType = "integer")
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultSeededResponse]))
  )
  @Path(value = "/{questionId}/featured-proposals")
  def featuredProposals: Route

  @ApiOperation(value = "get-keywords", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer", required = true)
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[Keyword]])))
  @Path(value = "/{questionId}/keywords")
  def getKeywords: Route

  def routes: Route =
    questionDetails ~ searchQuestions ~ getPopularTags ~ getTopProposals ~ getPartners ~
      getPersonalities ~ getTopIdeas ~ getTopIdea ~ listQuestions ~ featuredProposals ~ getKeywords
}

trait DefaultQuestionApiComponent
    extends QuestionApiComponent
    with SequenceServiceComponent
    with MakeAuthenticationDirectives
    with Logging
    with ParameterExtractors {

  this: MakeDirectivesDependencies
    with QuestionServiceComponent
    with OperationServiceComponent
    with OperationOfQuestionSearchEngineComponent
    with OperationOfQuestionServiceComponent
    with PartnerServiceComponent
    with FeatureServiceComponent
    with ActiveFeatureServiceComponent
    with ProposalSearchEngineComponent
    with TagServiceComponent
    with ProposalServiceComponent
    with TopIdeaCommentServiceComponent
    with PersonalityRoleServiceComponent
    with KeywordServiceComponent =>

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
                provideAsyncOrNotFound(
                  elasticsearchOperationOfQuestionAPI.findOperationOfQuestionById(question.questionId)
                ) { indexedOOQ =>
                  provideAsyncOrNotFound(operationService.findOne(operationOfQuestion.operationId)) { operation =>
                    provideAsync(
                      partnerService.find(
                        questionId = Some(question.questionId),
                        organisationId = None,
                        start = Start.zero,
                        end = None,
                        sort = Some("weight"),
                        order = Some(Order.desc),
                        partnerKind = None
                      )
                    ) { partners =>
                      provideAsync(findQuestionsOfOperation(operationOfQuestion.operationId)) { questions =>
                        provideAsync(findActiveFeatureSlugsByQuestionId(question.questionId)) { activeFeatureSlugs =>
                          provideAsync(findActiveFeatureData(question, activeFeatureSlugs)) { activeFeaturesData =>
                            def zoneCount(zone: Zone) = elasticsearchProposalAPI.countProposals(
                              SearchQuery(filters = Some(
                                SearchFilters(
                                  minScoreLowerBound = Option
                                    .when(zone == Zone.Consensus)(
                                      indexedOOQ.top20ConsensusThreshold.map(MinScoreLowerBoundSearchFilter)
                                    )
                                    .flatten,
                                  question = Some(QuestionSearchFilter(Seq(question.questionId))),
                                  sequencePool = Some(SequencePoolSearchFilter(SequencePool.Tested)),
                                  zone = Some(ZoneSearchFilter(zone))
                                )
                              )
                              )
                            )
                            val futureConsensusCount = zoneCount(Zone.Consensus)
                            val futureControversyCount = zoneCount(Zone.Controversy)
                            provideAsync(futureConsensusCount) { consensusCount =>
                              provideAsync(futureControversyCount) { controversyCount =>
                                complete(
                                  QuestionDetailsResponse(
                                    question,
                                    operation,
                                    operationOfQuestion,
                                    partners,
                                    questions,
                                    activeFeatureSlugs,
                                    controversyCount,
                                    consensusCount,
                                    activeFeaturesData
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
              consultationImageAlt = operationOfQuestion.consultationImageAlt,
              descriptionImage = operationOfQuestion.descriptionImage,
              descriptionImageAlt = operationOfQuestion.descriptionImageAlt,
              countries = question.countries,
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

    private def findActiveFeatureSlugsByQuestionId(questionId: QuestionId): Future[Seq[FeatureSlug]] = {
      activeFeatureService.find(maybeQuestionId = Some(Seq(questionId))).flatMap { activeFeatures =>
        featureService.findByFeatureIds(activeFeatures.map(_.featureId)).map(_.map(_.slug))
      }
    }

    private def findActiveFeatureData(question: Question, features: Seq[FeatureSlug]): Future[ActiveFeatureData] = {
      val futureTopProposal: Future[Option[ProposalResponse]] = if (features.contains(DisplayIntroCardWidget)) {
        proposalService
          .search(
            userId = None,
            query = SearchQuery(
              limit = Some(1),
              sortAlgorithm = Some(PopularAlgorithm),
              filters = Some(SearchFilters(question = Some(QuestionSearchFilter(Seq(question.questionId)))))
            ),
            requestContext = RequestContext.empty
          )
          .map(_.results)
          .map {
            case Seq() => None
            case proposal +: _ =>
              Some(
                ProposalResponse(
                  indexedProposal = proposal,
                  myProposal = false,
                  voteAndQualifications = None,
                  proposalKey = "not-votable"
                )
              )
          }
      } else {
        Future.successful(None)
      }

      for {
        topProposal <- futureTopProposal
      } yield ActiveFeatureData(topProposal)
    }

    override def searchQuestions: Route = get {
      path("questions" / "search") {
        makeOperation("GetQuestionDetails") { _ =>
          parameters(
            "questionIds".as[Seq[QuestionId]].?,
            "questionContent".?,
            "description".?,
            "operationKinds".as[Seq[OperationKind]].?,
            "language".as[Language].?,
            "country".as[Country].?,
            "limit".as[Int].?,
            "skip".as[Int].?,
            "sort".as[OperationOfQuestionElasticsearchFieldName].?,
            "order".as[Order].?
          ) {
            (
              questionIds: Option[Seq[QuestionId]],
              questionContent: Option[String],
              description: Option[String],
              operationKinds: Option[Seq[OperationKind]],
              language: Option[Language],
              country: Option[Country],
              limit: Option[Int],
              skip: Option[Int],
              sort: Option[OperationOfQuestionElasticsearchFieldName],
              order: Option[Order]
            ) =>
              Validation.validate(sort.map(Validation.validateSort("sort")).toList: _*)
              val filters: Option[OperationOfQuestionSearchFilters] = Some(
                OperationOfQuestionSearchFilters(
                  questionIds = questionIds.map(QuestionIdsSearchFilter.apply),
                  question = questionContent.map(QuestionContentSearchFilter(_, Some(Fuzziness.Auto))),
                  description = description.map(DescriptionSearchFilter.apply),
                  country = country.map(CountrySearchFilter.apply),
                  language = language.map(LanguageSearchFilter.apply),
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
          parameters("limit".as[Int].?, "skip".as[Int].?) { (limit: Option[Int], skip: Option[Int]) =>
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
          parameters("limit".as[Int].?, "mode".as[TopProposalsMode].?) {
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
          parameters("sortAlgorithm".as[String].?, "partnerKind".as[PartnerKind].?, "limit".as[Int].?, "skip".as[Int].?) {
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
                      start = Start.zero,
                      end = Some(End(1000)),
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
            parameters("personalityRole".as[String].?, "limit".as[Limit].?, "skip".as[Start].?) {
              (personalityRole: Option[String], limit: Option[Limit], skip: Option[Start]) =>
                provideAsync(
                  personalityRoleService
                    .find(
                      start = Start.zero,
                      end = None,
                      sort = None,
                      order = None,
                      roleIds = None,
                      name = personalityRole
                    )
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
                      start = skip.orZero,
                      end = limit.map(_.toEnd(skip.orZero)),
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
            parameters("limit".as[Limit].?, "skip".as[Start].?, "seed".as[Int].?) {
              (limit: Option[Limit], skip: Option[Start], seed: Option[Int]) =>
                provideAsync(
                  questionService
                    .getTopIdeas(skip.orZero, limit.map(_.toEnd(skip.orZero)), seed, questionId)
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
              "country".as[Country],
              "language".as[Language].?,
              "status".as[OperationOfQuestion.Status].?,
              "limit".as[Int].?,
              "skip".as[Int].?,
              "sortAlgorithm".as[SortAlgorithm].?
            ) {
              (
                country: Country,
                maybeLanguage: Option[Language],
                maybeStatus: Option[OperationOfQuestion.Status],
                limit: Option[Int],
                skip: Option[Int],
                sortAlgorithm: Option[SortAlgorithm]
              ) =>
                val filters = OperationOfQuestionSearchFilters(
                  country = Some(CountrySearchFilter(country)),
                  language = maybeLanguage.map(LanguageSearchFilter.apply),
                  status = maybeStatus.map(status => StatusSearchFilter(status))
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

    override def featuredProposals: Route = get {
      path("questions" / questionId / "featured-proposals") { questionId =>
        makeOperation("GetFeaturedProposals") { requestContext =>
          parameters("maxPartnerProposals".as[Int], "limit".as[Int], "seed".as[Int].?) {
            (maxPartnerProposals: Int, limit: Int, seed: Option[Int]) =>
              Validation.validate(
                validateField(
                  "maxPartnerProposals",
                  "invalid_value",
                  maxPartnerProposals >= 0,
                  "maxPartnerProposals should be positive"
                ),
                validateField(
                  "maxPartnerProposals",
                  "invalid_value",
                  maxPartnerProposals <= limit,
                  "maxPartnerProposals should be lower or equal to limit"
                ),
                validateField("limit", "invalid_value", limit > 0, "limit should be strictly positive")
              )
              optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
                provideAsyncOrNotFound(questionService.getQuestion(questionId)) { _ =>
                  provideAsync(
                    proposalService.questionFeaturedProposals(
                      questionId = questionId,
                      maxPartnerProposals = maxPartnerProposals,
                      limit = limit,
                      seed = seed,
                      maybeUserId = userAuth.map(_.user.userId),
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

    override def getKeywords: Route = get {
      path("questions" / questionId / "keywords") { questionId =>
        makeOperation("GetKeywords") { _ =>
          parameters("limit".as[Int]) { (limit: Int) =>
            provideAsyncOrNotFound(questionService.getQuestion(questionId)) { _ =>
              provideAsync(keywordService.findTop(questionId, limit)) { keywords =>
                complete(keywords)
              }
            }
          }
        }
      }
    }

  }
}

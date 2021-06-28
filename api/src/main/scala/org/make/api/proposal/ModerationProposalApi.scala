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

package org.make.api.proposal

import java.time.ZonedDateTime
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import grizzled.slf4j.Logging
import io.swagger.annotations._

import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.idea.IdeaServiceComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.semantic.SimilarIdea
import org.make.api.technical.{MakeAuthenticationDirectives, ReadJournalComponent}
import org.make.api.technical.CsvReceptacle._
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.user.UserServiceComponent
import org.make.core.Order
import org.make.core.auth.UserRights
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.ProposalsSearchResult
import org.make.core.proposal.{ProposalId, ProposalKeywordKey, ProposalStatus, SearchQuery}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.tag.TagId
import org.make.core.user.Role.RoleAdmin
import org.make.core.user.{UserId, UserType}
import org.make.core.{
  BusinessConfig,
  DateHelper,
  HttpCodes,
  ParameterExtractors,
  Validation,
  ValidationError,
  ValidationFailedError
}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "ModerationProposal")
@Path(value = "/moderation/proposals")
trait ModerationProposalApi extends Directives {

  @ApiOperation(
    value = "get-moderation-proposal",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @Path(value = "/{proposalId}")
  def getModerationProposal: Route

  //noinspection ScalaStyle
  @ApiOperation(
    value = "moderation-search-proposals",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsSearchResult]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "createdBefore", paramType = "query", dataType = "date"),
      new ApiImplicitParam(name = "initialProposal", paramType = "query", dataType = "boolean"),
      new ApiImplicitParam(name = "tagsIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "operationId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "ideaId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "trending", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "content", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "source", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "location", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "question", paramType = "query", dataType = "string"),
      new ApiImplicitParam(
        name = "status",
        paramType = "query",
        dataType = "string",
        allowableValues = "Pending,Accepted,Refused,Postponed,Archived",
        allowMultiple = true
      ),
      new ApiImplicitParam(name = "minVotesCount", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "toEnrich", paramType = "query", dataType = "boolean"),
      new ApiImplicitParam(name = "minScore", paramType = "query", dataType = "float"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(
        name = "userTypes",
        paramType = "query",
        dataType = "string",
        allowableValues = "USER,ORGANISATION,PERSONALITY",
        allowMultiple = true
      ),
      new ApiImplicitParam(name = "keywords", paramType = "query", dataType = "string")
    )
  )
  def searchAllProposals: Route

  @ApiOperation(
    value = "update-proposal",
    httpMethod = "PUT",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.UpdateProposalRequest"
      ),
      new ApiImplicitParam(name = "proposalId", paramType = "path", required = true, value = "", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @Path(value = "/{proposalId}")
  def updateProposal: Route

  @ApiOperation(
    value = "validate-proposal",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.ValidateProposalRequest"
      ),
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @Path(value = "/{proposalId}/accept")
  def acceptProposal: Route

  @ApiOperation(
    value = "refuse-proposal",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.RefuseProposalRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @Path(value = "/{proposalId}/refuse")
  def refuseProposal: Route

  @ApiOperation(
    value = "postpone-proposal",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @Path(value = "/{proposalId}/postpone")
  def postponeProposal: Route

  @ApiOperation(
    value = "lock-proposal",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @Path(value = "/{proposalId}/lock")
  def lock: Route

  @ApiOperation(
    value = "duplicates",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SimilarIdea])))
  @Path(value = "/{proposalId}/duplicates")
  def getDuplicates: Route

  @ApiOperation(
    value = "update-proposals-to-idea",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "moderator", description = "BO Moderator"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.PatchProposalsIdeaRequest"
      )
    )
  )
  @Path(value = "/change-idea")
  def changeProposalsIdea: Route

  @ApiOperation(
    value = "next-proposal-to-moderate",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "moderator", description = "BO Moderator"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.NextProposalToModerateRequest"
      )
    )
  )
  @Path(value = "/next")
  def nextProposalToModerate: Route

  @ApiOperation(
    value = "get-predicted-tags-for-proposal",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "moderator", description = "BO Moderator"))
      )
    )
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TagsForProposalResponse]))
  )
  @Path(value = "/{proposalId}/tags")
  def getTagsForProposal: Route

  def getPredictedTagsForProposal: Route

  def routes: Route =
    searchAllProposals ~
      updateProposal ~
      acceptProposal ~
      refuseProposal ~
      postponeProposal ~
      lock ~
      getDuplicates ~
      changeProposalsIdea ~
      getModerationProposal ~
      nextProposalToModerate ~
      getTagsForProposal ~
      getPredictedTagsForProposal
}

trait ModerationProposalApiComponent {
  def moderationProposalApi: ModerationProposalApi
}

trait DefaultModerationProposalApiComponent
    extends ModerationProposalApiComponent
    with MakeAuthenticationDirectives
    with Logging
    with ParameterExtractors {
  this: ProposalServiceComponent
    with MakeDirectivesDependencies
    with UserServiceComponent
    with IdeaServiceComponent
    with QuestionServiceComponent
    with OperationServiceComponent
    with ProposalCoordinatorServiceComponent
    with ReadJournalComponent
    with ActorSystemComponent =>

  override lazy val moderationProposalApi: DefaultModerationProposalApi = new DefaultModerationProposalApi

  class DefaultModerationProposalApi extends ModerationProposalApi {

    val moderationProposalId: PathMatcher1[ProposalId] = Segment.map(id => ProposalId(id))

    override def getModerationProposal: Route = {
      get {
        path("moderation" / "proposals" / moderationProposalId) { proposalId =>
          makeOperation("GetModerationProposal") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                provideAsyncOrNotFound(proposalService.getModerationProposalById(proposalId)) {
                  moderationProposalResponse =>
                    requireRightsOnQuestion(auth.user, moderationProposalResponse.questionId) {
                      complete(moderationProposalResponse)
                    }
                }
              }
            }
          }

        }
      }
    }

    override def searchAllProposals: Route = {
      get {
        path("moderation" / "proposals") {
          makeOperation("SearchAll") { requestContext =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireModerationRole(userAuth.user) {
                parameters(
                  "proposalIds".as[Seq[ProposalId]].?,
                  "createdBefore".as[ZonedDateTime].?,
                  "initialProposal".as[Boolean].?,
                  "tagsIds".as[Seq[TagId]].?,
                  "operationId".as[OperationId].?,
                  "questionId".as[Seq[QuestionId]].?,
                  "ideaId".as[Seq[IdeaId]].?,
                  "content".?,
                  "source".?,
                  "location".?,
                  "question".?,
                  "status".csv[ProposalStatus],
                  "minVotesCount".as[Int].?,
                  "toEnrich".as[Boolean].?,
                  "minScore".as[Double].?,
                  "language".as[Language].?,
                  "country".as[Country].?,
                  "userType".csv[UserType],
                  "keywords".as[Seq[ProposalKeywordKey]].?,
                  "userId".as[UserId].?
                ) {
                  (
                    proposalIds: Option[Seq[ProposalId]],
                    createdBefore: Option[ZonedDateTime],
                    initialProposal: Option[Boolean],
                    tagsIds: Option[Seq[TagId]],
                    operationId: Option[OperationId],
                    questionIds: Option[Seq[QuestionId]],
                    ideaId: Option[Seq[IdeaId]],
                    content: Option[String],
                    source: Option[String],
                    location: Option[String],
                    question: Option[String],
                    status: Option[Seq[ProposalStatus]],
                    minVotesCount: Option[Int],
                    toEnrich: Option[Boolean],
                    minScore: Option[Double],
                    language: Option[Language],
                    country: Option[Country],
                    userTypes: Option[Seq[UserType]],
                    keywords: Option[Seq[ProposalKeywordKey]],
                    userId: Option[UserId]
                  ) =>
                    parameters("limit".as[Int].?, "skip".as[Int].?, "sort".?, "order".as[Order].?) {
                      (limit: Option[Int], skip: Option[Int], sort: Option[String], order: Option[Order]) =>
                        Validation.validate(Seq(country.map { countryValue =>
                          Validation.validChoices(
                            fieldName = "country",
                            message = Some(
                              s"Invalid country. Expected one of ${BusinessConfig.supportedCountries.map(_.countryCode)}"
                            ),
                            Seq(countryValue),
                            BusinessConfig.supportedCountries.map(_.countryCode)
                          )
                        }, sort.map { sortValue =>
                          val choices =
                            Seq(
                              "content",
                              "slug",
                              "status",
                              "createdAt",
                              "updatedAt",
                              "trending",
                              "labels",
                              "country",
                              "language"
                            )
                          Validation.validChoices(
                            fieldName = "sort",
                            message = Some(
                              s"Invalid sort. Got $sortValue but expected one of: ${choices.mkString("\"", "\", \"", "\"")}"
                            ),
                            Seq(sortValue),
                            choices
                          )
                        }).flatten: _*)

                        val resolvedQuestions: Option[Seq[QuestionId]] = {
                          if (userAuth.user.roles.contains(RoleAdmin)) {
                            questionIds
                          } else {
                            questionIds.map { questions =>
                              questions.filter(id => userAuth.user.availableQuestions.contains(id))
                            }.orElse(Some(userAuth.user.availableQuestions))
                          }
                        }

                        val contextFilterRequest: Option[ContextFilterRequest] =
                          ContextFilterRequest.parse(operationId, source, location, question)

                        val exhaustiveSearchRequest: ExhaustiveSearchRequest = ExhaustiveSearchRequest(
                          proposalIds = proposalIds,
                          initialProposal = initialProposal,
                          tagsIds = tagsIds,
                          operationId = operationId,
                          questionIds = resolvedQuestions,
                          ideaIds = ideaId,
                          content = content,
                          context = contextFilterRequest,
                          status = status,
                          minVotesCount = minVotesCount,
                          toEnrich = toEnrich,
                          minScore = minScore,
                          language = language,
                          country = country,
                          sort = sort,
                          order = order,
                          limit = limit,
                          skip = skip,
                          createdBefore = createdBefore,
                          userTypes = userTypes,
                          keywords = keywords,
                          userId = userId
                        )
                        val query: SearchQuery = exhaustiveSearchRequest.toSearchQuery(requestContext)
                        provideAsync(
                          proposalService
                            .search(userId = Some(userAuth.user.userId), query = query, requestContext = requestContext)
                        ) { proposals =>
                          complete(proposals)
                        }
                    }
                }
              }
            }
          }
        }
      }
    }

    override def updateProposal: Route = put {
      path("moderation" / "proposals" / moderationProposalId) { proposalId =>
        makeOperation("EditProposal") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              decodeRequest {
                entity(as[UpdateProposalRequest]) { request =>
                  provideAsync(retrieveQuestion(request.questionId, proposalId)) { question =>
                    requireRightsOnQuestion(userAuth.user, Some(question.questionId)) {
                      provideAsyncOrNotFound(
                        proposalService.update(
                          proposalId = proposalId,
                          moderator = userAuth.user.userId,
                          requestContext = requestContext,
                          updatedAt = DateHelper.now(),
                          question = question,
                          newContent = request.newContent,
                          tags = request.tags,
                          idea = request.idea,
                          predictedTags = request.predictedTags,
                          predictedTagsModelName = request.predictedTagsModelName
                        )
                      ) { moderationProposalResponse: ModerationProposalResponse =>
                        complete(moderationProposalResponse)
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

    private def retrieveQuestion(maybeQuestionId: Option[QuestionId], proposalId: ProposalId): Future[Question] = {
      maybeQuestionId match {
        case Some(questionId) =>
          questionService.getQuestion(questionId).flatMap {
            case Some(question) => Future.successful(question)
            case _ =>
              Future.failed(
                ValidationFailedError(
                  Seq(ValidationError("questionId", "not_found", Some(s"question '$questionId' not found")))
                )
              )
          }
        case _ =>
          proposalCoordinatorService.getProposal(proposalId).flatMap {
            case Some(proposal) =>
              proposal.questionId match {
                case Some(questionId) =>
                  questionService.getQuestion(questionId).flatMap {
                    case Some(question) => Future.successful(question)
                    case _ =>
                      Future.failed(
                        new IllegalStateException(s"question '$questionId' not found for proposal '$proposalId'")
                      )
                  }
                case _ => Future.failed(new IllegalStateException(s"proposal '$proposalId' has no questionId"))
              }
            case _ =>
              Future.failed(
                ValidationFailedError(
                  Seq(ValidationError("questionId", "not_found", Some(s"proposal '$proposalId' not found")))
                )
              )
          }
      }
    }

    override def acceptProposal: Route = post {
      path("moderation" / "proposals" / moderationProposalId / "accept") { proposalId =>
        makeOperation("ValidateProposal") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[ValidateProposalRequest]) { request =>
                  provideAsync(retrieveQuestion(request.questionId, proposalId)) { question =>
                    requireRightsOnQuestion(auth.user, Some(question.questionId)) {
                      provideAsyncOrNotFound(
                        proposalService.validateProposal(
                          proposalId = proposalId,
                          moderator = auth.user.userId,
                          requestContext = requestContext,
                          question = question,
                          newContent = request.newContent,
                          sendNotificationEmail = request.sendNotificationEmail,
                          idea = request.idea,
                          tags = request.tags,
                          predictedTags = request.predictedTags,
                          predictedTagsModelName = request.predictedTagsModelName
                        )
                      ) { moderationProposalResponse: ModerationProposalResponse =>
                        complete(moderationProposalResponse)
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

    override def refuseProposal: Route = post {
      path("moderation" / "proposals" / moderationProposalId / "refuse") { proposalId =>
        makeOperation("RefuseProposal") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              provideAsyncOrNotFound(proposalCoordinatorService.getProposal(proposalId)) { proposal =>
                requireRightsOnQuestion(auth.user, proposal.questionId) {
                  decodeRequest {
                    entity(as[RefuseProposalRequest]) { refuseProposalRequest =>
                      provideAsyncOrNotFound(
                        proposalService.refuseProposal(
                          proposalId = proposalId,
                          moderator = auth.user.userId,
                          requestContext = requestContext,
                          request = refuseProposalRequest
                        )
                      ) { moderationProposalResponse: ModerationProposalResponse =>
                        complete(moderationProposalResponse)
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

    override def postponeProposal: Route = post {
      path("moderation" / "proposals" / moderationProposalId / "postpone") { proposalId =>
        makeOperation("PostponeProposal") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              provideAsyncOrNotFound(proposalCoordinatorService.getProposal(proposalId)) { proposal =>
                requireRightsOnQuestion(auth.user, proposal.questionId) {
                  decodeRequest {
                    provideAsyncOrNotFound(
                      proposalService
                        .postponeProposal(
                          proposalId = proposalId,
                          moderator = auth.user.userId,
                          requestContext = requestContext
                        )
                    ) { moderationProposalResponse: ModerationProposalResponse =>
                      complete(moderationProposalResponse)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def lock: Route = post {
      path("moderation" / "proposals" / moderationProposalId / "lock") { proposalId =>
        makeOperation("LockProposal") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              provideAsyncOrNotFound(proposalCoordinatorService.getProposal(proposalId)) { proposal =>
                requireRightsOnQuestion(auth.user, proposal.questionId) {
                  provideAsyncOrNotFound(
                    proposalService
                      .lockProposal(
                        proposalId = proposalId,
                        moderatorId = auth.user.userId,
                        requestContext = requestContext
                      )
                  ) { _ =>
                    complete(StatusCodes.NoContent)
                  }
                }
              }
            }
          }
        }
      }
    }

    override def getDuplicates: Route = {
      get {
        path("moderation" / "proposals" / moderationProposalId / "duplicates") { proposalId =>
          makeOperation("Duplicates") { requestContext =>
            makeOAuth2 { auth =>
              requireModerationRole(auth.user) {
                provideAsyncOrNotFound(proposalService.getProposalById(proposalId, requestContext)) { proposal =>
                  requireRightsOnQuestion(auth.user, proposal.question.map(_.questionId)) {
                    provideAsync(proposalService.getSimilar(auth.user.userId, proposal, requestContext)) { proposals =>
                      complete(proposals)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    override def changeProposalsIdea: Route = post {
      path("moderation" / "proposals" / "change-idea") {
        makeOperation("ChangeProposalsIdea") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[PatchProposalsIdeaRequest]) { changes =>
                  provideAsync(ideaService.fetchOne(changes.ideaId)) { maybeIdea =>
                    Validation.validate(
                      Validation
                        .requirePresent(fieldValue = maybeIdea, fieldName = "ideaId", message = Some("Invalid idea id"))
                    )
                    provideAsync(Future.sequence(changes.proposalIds.map(proposalService.getModerationProposalById))) {
                      proposals =>
                        val invalidProposalIdValues: Seq[String] =
                          changes.proposalIds.map(_.value).diff(proposals.flatten.map(_.proposalId.value))
                        val invalidProposalIdValuesString: String = invalidProposalIdValues.mkString(", ")
                        Validation.validate(
                          Validation.validateField(
                            field = "proposalIds",
                            "invalid_value",
                            message = s"Some proposal ids are invalid: $invalidProposalIdValuesString",
                            condition = invalidProposalIdValues.isEmpty
                          )
                        )
                        provideAsync(
                          proposalService
                            .changeProposalsIdea(changes.proposalIds, moderatorId = auth.user.userId, changes.ideaId)
                        ) { updatedProposals =>
                          val proposalIdsDiff: Seq[String] =
                            changes.proposalIds.map(_.value).diff(updatedProposals.map(_.proposalId.value))
                          if (proposalIdsDiff.nonEmpty) {
                            logger
                              .warn(
                                s"Some proposals are not updated during change idea operation: ${proposalIdsDiff.mkString(", ")}"
                              )
                          }
                          complete(StatusCodes.NoContent)
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

    override def nextProposalToModerate: Route = post {
      path("moderation" / "proposals" / "next") {
        makeOperation("NextProposalToModerate") { context =>
          makeOAuth2 { user =>
            requireModerationRole(user.user) {
              decodeRequest {
                entity(as[NextProposalToModerateRequest]) { request =>
                  provideAsyncOrNotFound {
                    request.questionId.map { questionId =>
                      questionService.getQuestion(questionId)
                    }.getOrElse(Future.successful(None))
                  } { question =>
                    requireRightsOnQuestion(user.user, Some(question.questionId)) {
                      provideAsyncOrNotFound(
                        proposalService.searchAndLockProposalToModerate(
                          question.questionId,
                          user.user.userId,
                          context,
                          request.toEnrich,
                          request.minVotesCount,
                          request.minScore
                        )
                      ) { proposal =>
                        complete(proposal)
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

    override def getTagsForProposal: Route = get {
      path("moderation" / "proposals" / moderationProposalId / "tags") { moderationProposalId =>
        makeOperation("GetTagsForProposal") { _ =>
          makeOAuth2 { user =>
            requireModerationRole(user.user) {
              provideAsyncOrNotFound(proposalCoordinatorService.getProposal(moderationProposalId)) { proposal =>
                requireRightsOnQuestion(user.user, proposal.questionId) {
                  provideAsync(proposalService.getTagsForProposal(proposal)) { tagsForProposalResponse =>
                    complete(tagsForProposalResponse)
                  }
                }
              }
            }
          }
        }
      }
    }

    override def getPredictedTagsForProposal: Route = get {
      path("moderation" / "proposals" / moderationProposalId / "predicted-tags") { moderationProposalId =>
        makeOperation("GetPredictedTagsForProposal") { _ =>
          makeOAuth2 { user =>
            requireModerationRole(user.user) {
              provideAsyncOrNotFound(proposalCoordinatorService.getProposal(moderationProposalId)) { proposal =>
                requireRightsOnQuestion(user.user, proposal.questionId) {
                  provideAsync(proposalService.getTagsForProposal(proposal)) { tagsForProposalResponse =>
                    complete(tagsForProposalResponse)
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

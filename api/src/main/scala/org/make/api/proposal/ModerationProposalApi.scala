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
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.idea.IdeaServiceComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.semantic.SimilarIdea
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, ReadJournalComponent}
import org.make.api.theme.ThemeServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.auth.UserRights
import org.make.core.common.indexed.{Order, SortRequest}
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.ProposalsSearchResult
import org.make.core.proposal.{ProposalId, ProposalStatus, SearchQuery}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.Role.RoleAdmin
import org.make.core.{DateHelper, HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

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
      new ApiImplicitParam(name = "createdBefore", paramType = "query", dataType = "string"),
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
      new ApiImplicitParam(name = "status", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "minVotesCount", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "toEnrich", paramType = "query", dataType = "boolean"),
      new ApiImplicitParam(name = "minScore", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string")
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
    value = "update-proposal-votes-verified",
    httpMethod = "PUT",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.UpdateProposalVotesVerifiedRequest"
      ),
      new ApiImplicitParam(name = "proposalId", paramType = "path", required = true, value = "", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @Path(value = "/{proposalId}/votes-verified")
  def updateProposalVotesVerified: Route

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
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.RefuseProposalRequest"
      ),
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "Ok")))
  @Path(value = "/{proposalId}/lock")
  def lock: Route

  @ApiOperation(
    value = "patch-proposal",
    httpMethod = "PATCH",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationProposalResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.proposal.PatchProposalRequest")
    )
  )
  @Path(value = "/{proposalId}")
  def patchProposal: Route

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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "Ok")))
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
  @Path(value = "/{proposalId}/predicted-tags")
  def getPredictedTagsForProposal: Route

  def routes: Route =
    searchAllProposals ~
      updateProposal ~
      acceptProposal ~
      refuseProposal ~
      postponeProposal ~
      lock ~
      patchProposal ~
      getDuplicates ~
      changeProposalsIdea ~
      getModerationProposal ~
      nextProposalToModerate ~
      getPredictedTagsForProposal ~
      updateProposalVotesVerified
}

trait ModerationProposalApiComponent {
  def moderationProposalApi: ModerationProposalApi
}

trait DefaultModerationProposalApiComponent
    extends ModerationProposalApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {
  this: ProposalServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with UserServiceComponent
    with IdeaServiceComponent
    with QuestionServiceComponent
    with ThemeServiceComponent
    with OperationServiceComponent
    with ProposalCoordinatorServiceComponent
    with ReadJournalComponent
    with ActorSystemComponent =>

  override lazy val moderationProposalApi: DefaultModerationProposalApi = new DefaultModerationProposalApi

  class DefaultModerationProposalApi extends ModerationProposalApi {

    val moderationProposalId: PathMatcher1[ProposalId] = Segment.flatMap(id => Try(ProposalId(id)).toOption)

    def getModerationProposal: Route = {
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

    def searchAllProposals: Route = {
      get {
        path("moderation" / "proposals") {
          makeOperation("SearchAll") { requestContext =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireModerationRole(userAuth.user) {
                parameters(
                  (
                    'proposalIds.as[immutable.Seq[ProposalId]].?,
                    'createdBefore.as[ZonedDateTime].?,
                    'initialProposal.as[Boolean].?,
                    'tagsIds.as[immutable.Seq[TagId]].?,
                    'operationId.as[OperationId].?,
                    'questionId.as[immutable.Seq[QuestionId]].?,
                    'ideaId.as[IdeaId].?,
                    'trending.?,
                    'content.?,
                    'source.?,
                    'location.?,
                    'question.?,
                    'status.as[immutable.Seq[ProposalStatus]].?,
                    'minVotesCount.as[Int].?,
                    'toEnrich.as[Boolean].?,
                    'minScore.as[Float].?,
                    'language.as[Language].?,
                    'country.as[Country].?,
                    'limit.as[Int].?,
                    'skip.as[Int].?,
                    'sort.?,
                    'order.?
                  )
                ) {
                  (proposalIds: Option[Seq[ProposalId]],
                   createdBefore: Option[ZonedDateTime],
                   initialProposal: Option[Boolean],
                   tagsIds: Option[Seq[TagId]],
                   operationId: Option[OperationId],
                   questionIds: Option[Seq[QuestionId]],
                   ideaId: Option[IdeaId],
                   trending: Option[String],
                   content: Option[String],
                   source: Option[String],
                   location: Option[String],
                   question: Option[String],
                   status: Option[Seq[ProposalStatus]],
                   minVotesCount: Option[Int],
                   toEnrich: Option[Boolean],
                   minScore: Option[Float],
                   language: Option[Language],
                   country: Option[Country],
                   limit: Option[Int],
                   skip: Option[Int],
                   sort: Option[String],
                   order: Option[String]) =>
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
                    }, order.map { orderValue =>
                      Validation.validChoices(
                        fieldName = "order",
                        message = Some(s"Invalid order. Expected one of: ${Order.orders.keys}"),
                        Seq(orderValue),
                        Order.orders.keys.toSeq
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
                      operationId.orElse(source).orElse(location).orElse(question).map { _ =>
                        ContextFilterRequest(operationId, source, location, question)
                      }
                    val sortRequest: Option[SortRequest] =
                      sort.orElse(order).map { _ =>
                        SortRequest(sort, order.flatMap(Order.matchOrder))
                      }
                    val exhaustiveSearchRequest: ExhaustiveSearchRequest = ExhaustiveSearchRequest(
                      proposalIds = proposalIds,
                      initialProposal = initialProposal,
                      tagsIds = tagsIds,
                      operationId = operationId,
                      questionIds = resolvedQuestions,
                      ideaId = ideaId,
                      trending = trending,
                      content = content,
                      context = contextFilterRequest,
                      status = status,
                      minVotesCount = minVotesCount,
                      toEnrich = toEnrich,
                      minScore = minScore,
                      language = language,
                      country = country,
                      sort = sortRequest,
                      limit = limit,
                      skip = skip,
                      createdBefore = createdBefore
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

    def updateProposal: Route = put {
      path("moderation" / "proposals" / moderationProposalId) { proposalId =>
        makeOperation("EditProposal") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              decodeRequest {
                entity(as[UpdateProposalRequest]) { request =>
                  provideAsyncOrNotFound(retrieveQuestion(request.questionId, proposalId, None, None)) { question =>
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

    private def retrieveQuestion(maybeQuestionId: Option[QuestionId],
                                 proposalId: ProposalId,
                                 maybeOperationId: Option[OperationId],
                                 maybeThemeId: Option[ThemeId]): Future[Option[Question]] = {

      maybeQuestionId.map { questionId =>
        questionService.getQuestion(questionId)
      }.getOrElse {
        proposalCoordinatorService.getProposal(proposalId).flatMap {
          case Some(proposal) =>
            val maybeFuture = for {
              country  <- proposal.country
              language <- proposal.language
            } yield {
              questionService.findQuestion(maybeThemeId, maybeOperationId, country, language)
            }
            maybeFuture.getOrElse {
              Future.successful(None)
            }
          case None =>
            Future.successful(None)
        }
      }
    }

    private def retrieveProposalQuestion(proposalId: ProposalId): Future[Option[Question]] = {
      proposalCoordinatorService.getProposal(proposalId).flatMap {
        case Some(proposal) =>
          proposal.questionId
            .map(questionService.getQuestion)
            .getOrElse(Future.successful(None))
        case None =>
          Future.successful(None)
      }
    }

    def updateProposalVotesVerified: Route = put {
      path("moderation" / "proposals" / moderationProposalId / "votes-verified") { proposalId =>
        makeOperation("EditProposalVotesVerified") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireAdminRole(userAuth.user) {
              decodeRequest {
                entity(as[UpdateProposalVotesVerifiedRequest]) { request =>
                  provideAsyncOrNotFound(retrieveProposalQuestion(proposalId)) { _ =>
                    provideAsyncOrNotFound(
                      proposalService.updateVotesVerified(
                        proposalId = proposalId,
                        moderator = userAuth.user.userId,
                        requestContext = requestContext,
                        updatedAt = DateHelper.now(),
                        votesVerified = request.votesVerified
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

    def acceptProposal: Route = post {
      path("moderation" / "proposals" / moderationProposalId / "accept") { proposalId =>
        makeOperation("ValidateProposal") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[ValidateProposalRequest]) { request =>
                  provideAsyncOrNotFound(retrieveQuestion(request.questionId, proposalId, None, None)) { question =>
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

    def refuseProposal: Route = post {
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

    def postponeProposal: Route = post {
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

    def lock: Route = post {
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

    def patchProposal: Route = {
      patch {
        path("moderation" / "proposals" / moderationProposalId) { id =>
          makeOperation("PatchProposal") { context =>
            makeOAuth2 { auth =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[PatchProposalRequest]) { patch =>
                    provideAsyncOrNotFound(proposalService.patchProposal(id, auth.user.userId, context, patch)) {
                      proposal =>
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

    def getDuplicates: Route = {
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

    def changeProposalsIdea: Route = post {
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
                              .warn("Some proposals are not updated during change idea operation: {}", proposalIdsDiff)
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

    def nextProposalToModerate: Route = post {
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

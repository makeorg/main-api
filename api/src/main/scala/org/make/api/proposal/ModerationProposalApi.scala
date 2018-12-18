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

import akka.http.scaladsl.model.headers.{`Content-Disposition`, ContentDispositionTypes}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.idea.IdeaServiceComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.semantic.SimilarIdea
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.businessconfig.BusinessConfig
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, ReadJournalComponent}
import org.make.api.theme.ThemeServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.auth.UserRights
import org.make.core.common.indexed.{Order, SortRequest}
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.indexed.ProposalsSearchResult
import org.make.core.proposal.{ProposalId, ProposalStatus, SearchQuery}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.TagId
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "proposalId", paramType = "path", dataType = "string")))
  @Path(value = "/{proposalId}")
  def getModerationProposal: Route

  @ApiOperation(
    value = "export-proposals",
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
  @Path(value = "/export")
  def exportProposals: Route

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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
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

  def routes: Route =
    searchAllProposals ~
      updateProposal ~
      acceptProposal ~
      refuseProposal ~
      postponeProposal ~
      exportProposals ~
      lock ~
      patchProposal ~
      getDuplicates ~
      changeProposalsIdea ~
      getModerationProposal ~
      nextProposalToModerate

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
    with UserServiceComponent
    with IdeaServiceComponent
    with QuestionServiceComponent
    with ThemeServiceComponent
    with OperationServiceComponent
    with ProposalCoordinatorServiceComponent
    with ReadJournalComponent
    with ActorSystemComponent =>

  override lazy val moderationProposalApi: ModerationProposalApi = new ModerationProposalApi {

    val moderationProposalId: PathMatcher1[ProposalId] = Segment.flatMap(id => Try(ProposalId(id)).toOption)

    def getModerationProposal: Route = {
      get {
        path("moderation" / "proposals" / moderationProposalId) { proposalId =>
          makeOperation("GetModerationProposal") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                provideAsyncOrNotFound(proposalService.getModerationProposalById(proposalId)) { proposalResponse =>
                  complete(proposalResponse)
                }
              }
            }
          }

        }
      }
    }

    def exportProposals: Route = {
      get {
        path("moderation" / "proposals" / "export") {
          makeOperation("ExportProposal") { requestContext =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                parameters(
                  (
                    'format, // TODO Use Accept header to get the format
                    'filename,
                    'tags.as[immutable.Seq[TagId]].?,
                    'content.?,
                    'operation.as[OperationId].?,
                    'source.?,
                    'question.?,
                    'language.as[Language].?
                  )
                ) {
                  (_: String,
                   fileName: String,
                   tags: Option[Seq[TagId]],
                   content: Option[String],
                   operation: Option[OperationId],
                   source: Option[String],
                   question: Option[String],
                   language: Option[Language]) =>
                    provideAsync(themeService.findAll()) { themes =>
                      provideAsync(
                        operationService.find(slug = None, country = None, maybeSource = None, openAt = None)
                      ) { operations =>
                        provideAsync(
                          proposalService.search(
                            userId = Some(auth.user.userId),
                            query = ExhaustiveSearchRequest(
                              tagsIds = tags,
                              content = content,
                              context =
                                Some(ContextFilterRequest(operation = operation, source = source, question = question)),
                              language = language,
                              status = Some(Seq(Accepted)),
                              limit = Some(5000) //TODO get limit value for export into config files
                            ).toSearchQuery(requestContext),
                            requestContext = requestContext
                          )
                        ) { proposals =>
                          { // TODO: add question
                            complete {
                              HttpResponse(
                                entity = HttpEntity(
                                  ContentTypes.`text/csv(UTF-8)`,
                                  ByteString(
                                    (Seq(ProposalCsvSerializer.proposalsCsvHeaders) ++ ProposalCsvSerializer
                                      .proposalsToRow(proposals.results, themes, operations)).mkString("\n")
                                  )
                                )
                              ).withHeaders(
                                `Content-Disposition`(
                                  ContentDispositionTypes.attachment,
                                  Map("filename" -> s"$fileName.csv")
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
                    'questionId.as[QuestionId].?,
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
                   questionId: Option[QuestionId],
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
                      questionId = questionId,
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
                  provideAsyncOrNotFound(
                    retrieveQuestion(request.questionId, proposalId, request.operation, request.theme)
                  ) { question =>
                    provideAsyncOrNotFound(
                      proposalService.update(
                        proposalId = proposalId,
                        moderator = userAuth.user.userId,
                        requestContext = requestContext,
                        updatedAt = DateHelper.now(),
                        question = question,
                        newContent = request.newContent,
                        labels = request.labels,
                        tags = request.tags,
                        idea = request.idea
                      )
                    ) { proposalResponse: ProposalResponse =>
                      complete(proposalResponse)
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

    def acceptProposal: Route = post {
      path("moderation" / "proposals" / moderationProposalId / "accept") { proposalId =>
        makeOperation("ValidateProposal") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[ValidateProposalRequest]) { request =>
                  provideAsyncOrNotFound(retrieveQuestion(None, proposalId, request.operation, request.theme)) {
                    question =>
                      provideAsyncOrNotFound(
                        proposalService.validateProposal(
                          proposalId = proposalId,
                          moderator = auth.user.userId,
                          requestContext = requestContext,
                          question = question,
                          newContent = request.newContent,
                          sendNotificationEmail = request.sendNotificationEmail,
                          idea = request.idea,
                          labels = request.labels,
                          tags = request.tags
                        )
                      ) { proposalResponse: ProposalResponse =>
                        complete(proposalResponse)
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
              decodeRequest {
                entity(as[RefuseProposalRequest]) { refuseProposalRequest =>
                  provideAsyncOrNotFound(
                    proposalService.refuseProposal(
                      proposalId = proposalId,
                      moderator = auth.user.userId,
                      requestContext = requestContext,
                      request = refuseProposalRequest
                    )
                  ) { proposalResponse: ProposalResponse =>
                    complete(proposalResponse)
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
              decodeRequest {
                provideAsyncOrNotFound(
                  proposalService.postponeProposal(
                    proposalId = proposalId,
                    moderator = auth.user.userId,
                    requestContext = requestContext
                  )
                ) { proposalResponse: ProposalResponse =>
                  complete(proposalResponse)
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
                  val proposals = proposalService.getSimilar(auth.user.userId, proposal, requestContext)
                  onSuccess(proposals)(complete(_))
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
            requireModerationRole(auth.user) {
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
                    questionService.findQuestionByQuestionIdOrThemeOrOperation(
                      request.questionId,
                      request.themeId,
                      request.operationId,
                      request.country,
                      request.language
                    )
                  } { question =>
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
}

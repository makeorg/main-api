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

import akka.http.scaladsl.model.headers.{`Content-Disposition`, ContentDispositionTypes}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.idea.IdeaServiceComponent
import org.make.api.operation.OperationServiceComponent
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
import org.make.core.proposal.{ProposalId, ProposalStatus}
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.{DateHelper, HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import org.make.api.question.QuestionServiceComponent

@Api(value = "ModerationProposal")
@Path(value = "/moderation/proposals")
trait ModerationProposalApi extends MakeAuthenticationDirectives with StrictLogging with ParameterExtractors {
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
                  'theme.as[immutable.Seq[ThemeId]].?,
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
                 themeId: Option[Seq[ThemeId]],
                 tags: Option[Seq[TagId]],
                 content: Option[String],
                 operation: Option[OperationId],
                 source: Option[String],
                 question: Option[String],
                 language: Option[Language]) =>
                  provideAsync(themeService.findAll()) { themes =>
                    provideAsync(operationService.find()) { operations =>
                      provideAsync(
                        proposalService.search(
                          userId = Some(auth.user.userId),
                          query = ExhaustiveSearchRequest(
                            themesIds = themeId,
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
                        {
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
      new ApiImplicitParam(name = "themesIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "tagsIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "labelsIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "operationId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "ideaId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "trending", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "content", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "source", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "location", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "question", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "status", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string")
    )
  )
  def searchAllProposals: Route = {
    get {
      path("moderation" / "proposals") {
        makeOperation("SearchAll") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              parameters(
                (
                  'proposalIds.as[immutable.Seq[ProposalId]].?,
                  'themesIds.as[immutable.Seq[ThemeId]].?,
                  'tagsIds.as[immutable.Seq[TagId]].?,
                  'labelsIds.as[immutable.Seq[LabelId]].?,
                  'operationId.as[OperationId].?,
                  'ideaId.as[IdeaId].?,
                  'trending.?,
                  'content.?,
                  'source.?,
                  'location.?,
                  'question.?,
                  'status.as[immutable.Seq[ProposalStatus]].?,
                  'language.as[Language].?,
                  'country.as[Country].?,
                  'limit.as[Int].?,
                  'skip.as[Int].?,
                  'sort.?,
                  'order.?
                )
              ) {
                (proposalIds: Option[Seq[ProposalId]],
                 themesIds: Option[Seq[ThemeId]],
                 tagsIds: Option[Seq[TagId]],
                 labelsIds: Option[Seq[LabelId]],
                 operationId: Option[OperationId],
                 ideaId: Option[IdeaId],
                 trending: Option[String],
                 content: Option[String],
                 source: Option[String],
                 location: Option[String],
                 question: Option[String],
                 status: Option[Seq[ProposalStatus]],
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
                  val sortRequest: Option[SortRequest] = sort.orElse(order).map { _ =>
                    SortRequest(sort, order.flatMap(Order.matchOrder))
                  }
                  val exhaustiveSearchRequest: ExhaustiveSearchRequest = ExhaustiveSearchRequest(
                    proposalIds = proposalIds,
                    themesIds = themesIds,
                    tagsIds = tagsIds,
                    labelsIds = labelsIds,
                    operationId = operationId,
                    ideaId = ideaId,
                    trending = trending,
                    content = content,
                    context = contextFilterRequest,
                    status = status,
                    language = language,
                    country = country,
                    sort = sortRequest,
                    limit = limit,
                    skip = skip
                  )
                  provideAsync(
                    proposalService.search(
                      userId = Some(userAuth.user.userId),
                      query = exhaustiveSearchRequest.toSearchQuery(requestContext),
                      requestContext = requestContext
                    )
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
  def updateProposal: Route =
    put {
      path("moderation" / "proposals" / moderationProposalId) { proposalId =>
        makeOperation("EditProposal") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              decodeRequest {
                entity(as[UpdateProposalRequest]) { request =>
                  provideAsyncOrNotFound(proposalCoordinatorService.getProposal(proposalId)) { proposal =>
                    provideAsyncOrNotFound(
                      questionService
                      // TODO less .get
                        .findQuestion(request.theme, request.operation, proposal.country.get, proposal.language.get)
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
    }

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
  def acceptProposal: Route = post {
    path("moderation" / "proposals" / moderationProposalId / "accept") { proposalId =>
      makeOperation("ValidateProposal") { requestContext =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
          requireModerationRole(auth.user) {
            decodeRequest {
              entity(as[ValidateProposalRequest]) { request =>
                provideAsyncOrNotFound(proposalCoordinatorService.getProposal(proposalId)) { proposal =>
                  provideAsyncOrNotFound(
                    questionService // TODO: less .get
                      .findQuestion(request.theme, request.operation, proposal.country.get, proposal.language.get)
                  ) { question =>
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
  }

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
  def lock: Route = post {
    path("moderation" / "proposals" / moderationProposalId / "lock") { proposalId =>
      makeOperation("LockProposal") { requestContext =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
          requireModerationRole(auth.user) {
            provideAsyncOrNotFound(
              proposalService
                .lockProposal(proposalId = proposalId, moderatorId = auth.user.userId, requestContext = requestContext)
            ) { _ =>
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }
  }

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
  def getDuplicates: Route = {
    get {
      path("moderation" / "proposals" / moderationProposalId / "duplicates") { proposalId =>
        makeOperation("Duplicates") { requestContext =>
          makeOAuth2 { auth =>
            requireModerationRole(auth.user) {
              provideAsync(
                proposalService.getSimilar(userId = auth.user.userId, proposalId = proposalId, requestContext)
              ) { proposals =>
                complete(proposals)
              }
            }
          }
        }
      }
    }
  }

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
  def nextProposalToModerate: Route = post {
    path("moderation" / "proposals" / "next") {
      makeOperation("NextProposalToModerate") { context =>
        makeOAuth2 { user =>
          requireModerationRole(user.user) {
            decodeRequest {
              entity(as[NextProposalToModerateRequest]) { request =>
                provideAsyncOrNotFound(
                  proposalService.searchAndLockProposalToModerate(
                    request.operationId,
                    request.themeId,
                    request.country,
                    request.language,
                    user.user.userId,
                    context
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

  val moderationProposalRoutes: Route =
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
      getDuplicates ~
      getModerationProposal ~
      nextProposalToModerate

  val moderationProposalId: PathMatcher1[ProposalId] =
    Segment.flatMap(id => Try(ProposalId(id)).toOption)

}

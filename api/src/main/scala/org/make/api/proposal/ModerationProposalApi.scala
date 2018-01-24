package org.make.api.proposal

import javax.ws.rs.Path

import akka.http.scaladsl.model.headers.{`Content-Disposition`, ContentDispositionTypes}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller.CsvSeq
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.idea.IdeaServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, ReadJournalComponent}
import org.make.api.theme.ThemeServiceComponent
import org.make.api.user.UserServiceComponent
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.indexed.ProposalsSearchResult
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.{DateHelper, HttpCodes, Validation}

import scala.concurrent.Future
import scala.util.Try
import scalaoauth2.provider.AuthInfo
import scala.concurrent.ExecutionContext.Implicits.global

@Api(value = "ModerationProposal")
@Path(value = "/moderation/proposals")
trait ModerationProposalApi extends MakeAuthenticationDirectives with StrictLogging {
  this: ProposalServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with UserServiceComponent
    with IdeaServiceComponent
    with ThemeServiceComponent
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
        makeTrace("GetModerationProposal") { _ =>
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
  @Path(value = "/moderation/proposals")
  def exportProposals: Route = {
    get {
      path("moderation" / "proposals") {
        makeTrace("ExportProposal") { requestContext =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              parameters(
                (
                  'format, // TODO Use Accept header to get the format
                  'filename,
                  'theme.as(CsvSeq[String]).?,
                  'tags.as(CsvSeq[String]).?,
                  'content.?,
                  'operation.?,
                  'source.?,
                  'question.?
                )
              ) { (_, fileName, themeId, tags, content, operation, source, question) =>
                provideAsync(themeService.findAll()) { themes =>
                  provideAsync(
                    proposalService.search(
                      userId = Some(auth.user.userId),
                      query = ExhaustiveSearchRequest(
                        themesIds = themeId.map(_.map(ThemeId(_))),
                        tagsIds = tags.map(_.map(TagId(_))),
                        content = content,
                        context = Some(
                          ContextFilterRequest(
                            operation = operation.map(OperationId(_)),
                            source = source,
                            question = question
                          )
                        ),
                        status = Some(Seq(Accepted)),
                        limit = Some(5000) //TODO get limit value for export into config files
                      ).toSearchQuery,
                      maybeSeed = None,
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
                                .proposalsToRow(proposals.results, themes)).mkString("\n")
                            )
                          )
                        ).withHeaders(
                          `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> s"$fileName.csv"))
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

  @ApiOperation(
    value = "moderation-search-proposals",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsSearchResult]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.proposal.ExhaustiveSearchRequest"
      )
    )
  )
  @Path(value = "/search")
  def searchAllProposals: Route = {
    post {
      path("moderation" / "proposals" / "search") {
        makeTrace("SearchAll") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              decodeRequest {
                entity(as[ExhaustiveSearchRequest]) { request: ExhaustiveSearchRequest =>
                  provideAsync(
                    proposalService.search(
                      userId = Some(userAuth.user.userId),
                      query = request.toSearchQuery,
                      maybeSeed = None,
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
        makeTrace("EditProposal") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              decodeRequest {
                entity(as[UpdateProposalRequest]) { request =>
                  provideAsyncOrNotFound(
                    proposalService.update(
                      proposalId = proposalId,
                      moderator = userAuth.user.userId,
                      requestContext = requestContext,
                      updatedAt = DateHelper.now(),
                      request = request
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
      makeTrace("ValidateProposal") { requestContext =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
          requireModerationRole(auth.user) {
            decodeRequest {
              entity(as[ValidateProposalRequest]) { request =>
                provideAsyncOrNotFound(
                  proposalService.validateProposal(
                    proposalId = proposalId,
                    moderator = auth.user.userId,
                    requestContext = requestContext,
                    request = request
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.proposal.RefuseProposalRequest")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalResponse]))
  )
  @Path(value = "/{proposalId}/refuse")
  def refuseProposal: Route = post {
    path("moderation" / "proposals" / moderationProposalId / "refuse") { proposalId =>
      makeTrace("RefuseProposal") { requestContext =>
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
      makeTrace("PostponeProposal") { requestContext =>
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
      makeTrace("LockProposal") { requestContext =>
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
    value = "remove-from-similars",
    httpMethod = "DELETE",
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
  @Path(value = "/similars/{proposalId}")
  @Deprecated
  def removeProposalFromClusters: Route = delete {
    path("moderation" / "proposals" / "similars" / moderationProposalId) { proposalId =>
      makeTrace("RemoveFromSimilars") { context =>
        makeOAuth2 { auth =>
          requireModerationRole(auth.user) {
            onSuccess(proposalService.removeProposalFromCluster(proposalId)) { _ =>
              complete(StatusCodes.OK)
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "remove-clusters",
    httpMethod = "DELETE",
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "Ok")))
  @Path(value = "/similars")
  @Deprecated
  def removeClusters: Route = delete {
    path("moderation" / "proposals" / "similars") {
      makeTrace("RemoveClusters") { context =>
        makeOAuth2 { auth =>
          requireModerationRole(auth.user) {
            onSuccess(proposalService.clearSimilarProposals()) { _ =>
              complete(StatusCodes.OK)
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
        makeTrace("PatchProposal") { context =>
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
  @Path(value = "/{proposalId}/duplicates")
  def getDuplicates: Route = {
    get {
      path("moderation" / "proposals" / moderationProposalId / "duplicates") { proposalId =>
        makeTrace("Duplicates") { requestContext =>
          makeOAuth2 { auth =>
            requireModerationRole(auth.user) {
              provideAsync(
                proposalService.getDuplicates(userId = auth.user.userId, proposalId = proposalId, requestContext)
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
      makeTrace("change proposals idea") { _ =>
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

  val moderationProposalRoutes: Route =
    getModerationProposal ~
      searchAllProposals ~
      updateProposal ~
      acceptProposal ~
      refuseProposal ~
      postponeProposal ~
      exportProposals ~
      removeProposalFromClusters ~
      removeClusters ~
      lock ~
      patchProposal ~
      getDuplicates ~
      changeProposalsIdea
  getDuplicates

  val moderationProposalId: PathMatcher1[ProposalId] =
    Segment.flatMap(id => Try(ProposalId(id)).toOption)

}

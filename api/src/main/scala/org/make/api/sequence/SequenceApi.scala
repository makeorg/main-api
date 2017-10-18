package org.make.api.sequence

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.theme.ThemeServiceComponent
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence._
import org.make.core.sequence.indexed.{IndexedStartSequence, SequencesSearchResult}
import org.make.core.user.User
import org.make.core.{DateHelper, HttpCodes, Validation}

import scala.concurrent.ExecutionContext.Implicits.global
import scalaoauth2.provider.AuthInfo

@Api(value = "Sequence")
@Path(value = "/")
trait SequenceApi extends MakeAuthenticationDirectives with StrictLogging {
  this: SequenceServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ThemeServiceComponent =>

  @ApiOperation(
    value = "moderation-get-sequence",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "sequenceId", paramType = "path", dataType = "string")))
  @Path(value = "/moderation/sequences/{sequenceId}")
  def getModerationSequence: Route = {
    get {
      path("moderation" / "sequences" / sequenceId) { sequenceId =>
        makeTrace("GetModerationSequence") { _ =>
          makeOAuth2 { auth: AuthInfo[User] =>
            requireModerationRole(auth.user) {
              provideAsyncOrNotFound(sequenceService.getModerationSequenceById(sequenceId)) { sequenceResponse =>
                complete(sequenceResponse)
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "moderation-post-sequence",
    httpMethod = "POST",
    code = HttpCodes.Created,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.sequence.CreateSequenceRequest")
    )
  )
  @Path(value = "/moderation/sequences")
  def postSequence: Route =
    post {
      path("moderation" / "sequences") {
        makeTrace("PostSequence") { requestContext =>
          makeOAuth2 { auth: AuthInfo[User] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[CreateSequenceRequest]) { request: CreateSequenceRequest =>
                  provideAsync(themeService.findAll()) { themes =>
                    val themeIds = request.themeIds.distinct
                    Validation.validate(
                      Validation.validChoices(
                        "themeIds",
                        Some("Some theme ids are invalid"),
                        themeIds,
                        themes.map(_.themeId.value)
                      )
                    )
                    onSuccess(
                      sequenceService
                        .create(
                          userId = auth.user.userId,
                          requestContext = requestContext,
                          createdAt = DateHelper.now(),
                          title = request.title,
                          tagIds = request.tagIds.map(TagId(_)),
                          themeIds = themeIds.map(ThemeId(_)),
                          searchable = request.searchable
                        )
                    ) {
                      case Some(sequenceResponse) => complete(StatusCodes.Created -> sequenceResponse)
                      case None                   => complete(StatusCodes.InternalServerError)
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
    value = "moderation-update-sequence",
    httpMethod = "PATCH",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.sequence.UpdateSequenceRequest"
      ),
      new ApiImplicitParam(name = "sequenceId", paramType = "path", required = true, value = "", dataType = "string")
    )
  )
  @Path(value = "/moderation/sequences/{sequenceId}")
  def patchSequence: Route =
    patch {
      path("moderation" / "sequences" / sequenceId) { sequenceId =>
        makeTrace("PatchSequence") { requestContext =>
          makeOAuth2 { auth: AuthInfo[User] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[UpdateSequenceRequest]) { request: UpdateSequenceRequest =>
                  if (request.status.nonEmpty) {
                    Validation.validate(
                      Validation.validChoices(
                        "status",
                        Some("Invalid status"),
                        Seq(request.status.get),
                        SequenceStatus.statusMap.keys.toList
                      )
                    )
                  }

                  provideAsyncOrNotFound(
                    sequenceService.update(
                      sequenceId = sequenceId,
                      moderatorId = auth.user.userId,
                      requestContext = requestContext,
                      title = request.title,
                      status = request.status.map(SequenceStatus.statusMap)
                    )
                  ) { sequenceResponse =>
                    complete(StatusCodes.OK -> sequenceResponse)
                  }
                }
              }
            }
          }
        }
      }
    }

  @ApiOperation(
    value = "moderation-add-proposal-sequence",
    httpMethod = "POST",
    code = HttpCodes.Created,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.sequence.AddProposalSequenceRequest"
      ),
      new ApiImplicitParam(name = "sequenceId", paramType = "path", required = true, value = "", dataType = "string")
    )
  )
  @Path(value = "/moderation/sequences/{sequenceId}/proposals/add")
  def postAddProposalSequence: Route =
    post {
      path("moderation" / "sequences" / sequenceId / "proposals" / "add") { sequenceId =>
        makeTrace("AddProposalsSequence") { requestContext =>
          makeOAuth2 { auth: AuthInfo[User] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[AddProposalSequenceRequest]) { request: AddProposalSequenceRequest =>
                  provideAsyncOrNotFound(
                    sequenceService.addProposals(
                      sequenceId = sequenceId,
                      moderatorId = auth.user.userId,
                      requestContext = requestContext,
                      proposalIds = request.proposalIds.map(ProposalId(_))
                    )
                  ) { sequenceResponse =>
                    complete(StatusCodes.OK -> sequenceResponse)
                  }
                }
              }
            }
          }
        }
      }
    }

  @ApiOperation(
    value = "moderation-remove-proposal-sequence",
    httpMethod = "POST",
    code = HttpCodes.Created,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.sequence.RemoveProposalSequenceRequest"
      ),
      new ApiImplicitParam(name = "sequenceId", paramType = "path", required = true, value = "", dataType = "string")
    )
  )
  @Path(value = "/moderation/sequences/{sequenceId}/proposals/remove")
  def postRemoveProposalSequence: Route =
    post {
      path("moderation" / "sequences" / sequenceId / "proposals" / "remove") { sequenceId =>
        makeTrace("AddProposalsSequence") { requestContext =>
          makeOAuth2 { auth: AuthInfo[User] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[RemoveProposalSequenceRequest]) { request: RemoveProposalSequenceRequest =>
                  provideAsyncOrNotFound(
                    sequenceService.removeProposals(
                      sequenceId = sequenceId,
                      moderatorId = auth.user.userId,
                      requestContext = requestContext,
                      proposalIds = request.proposalIds.map(ProposalId(_))
                    )
                  ) { sequenceResponse =>
                    complete(StatusCodes.OK -> sequenceResponse)
                  }
                }
              }
            }
          }
        }
      }
    }

  @ApiOperation(
    value = "moderation-post-search-sequences",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequencesSearchResult]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.sequence.ExhaustiveSearchRequest"
      )
    )
  )
  @Path(value = "/moderation/sequences/search")
  def searchAllSequences: Route = {
    post {
      path("moderation" / "sequences" / "search") {
        makeTrace("SearchAll") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[User] =>
            requireModerationRole(userAuth.user) {
              decodeRequest {
                entity(as[ExhaustiveSearchRequest]) { request: ExhaustiveSearchRequest =>
                  provideAsync(
                    sequenceService.search(Some(userAuth.user.userId), request.toSearchQuery, requestContext)
                  ) { sequences =>
                    complete(sequences)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "start-sequence-by-slug", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Option[IndexedStartSequence]]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "slug", paramType = "path", dataType = "string")))
  @Path(value = "/sequences/{slug}")
  def startSequenceBySlug: Route = {
    get {
      path("sequences" / sequenceSlug) { slug =>
        makeTrace("Search") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[User]] =>
            decodeRequest {
              // toDo: manage already voted proposals (session or user)
              val excludedProposals = Seq.empty
              provideAsyncOrNotFound(
                sequenceService
                  .startNewSequence(
                    maybeUserId = userAuth.map(_.user.userId),
                    slug = slug,
                    excludedProposals = excludedProposals,
                    requestContext = requestContext
                  )
                  .map(Option(_))
              ) { sequences =>
                complete(sequences)
              }
            }

          }
        }
      }
    }
  }

  val sequenceRoutes: Route =
    getModerationSequence ~
      postSequence ~
      searchAllSequences ~
      startSequenceBySlug ~
      postAddProposalSequence ~
      postRemoveProposalSequence ~
      patchSequence

  val sequenceId: PathMatcher1[SequenceId] = Segment.map(id => SequenceId(id))
  val sequenceSlug: PathMatcher1[String] = Segment
}

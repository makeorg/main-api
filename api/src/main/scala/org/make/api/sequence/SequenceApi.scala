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
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.{DateHelper, HttpCodes, Validation}
import org.make.core.sequence._
import org.make.core.user.User

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Sequence")
trait SequenceApi extends MakeAuthenticationDirectives with StrictLogging {
  this: SequenceServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ThemeServiceComponent =>

  @ApiOperation(value = "start-sequence", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Sequence])))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "sequenceId", paramType = "path", dataType = "string")))
  @Path(value = "/sequences/{sequenceId}")
  def startSequence: Route = {
    get {
      path("sequences" / sequenceId) { sequenceId =>
        makeTrace("StartSequence") { requestContext =>
          complete(StatusCodes.NotImplemented)
        }
      }
    }
  }

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
                          themeIds = themeIds.map(ThemeId(_))
                        )
                    ) { sequenceResponse =>
                      complete(StatusCodes.Created -> sequenceResponse)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

  /*
  @ApiOperation(value = "search-sequences", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[IndexedSequence]]))
  )
  @ApiImplicitParams(
    value =
      Array(new ApiImplicitParam(name = "body", paramType = "body", dataType = "org.make.api.sequence.SearchRequest"))
  )
  @Path(value = "/search")
  def search: Route = {
    post {
      path("sequences" / "search") {
        makeTrace("Search") { requestContext =>
          // TODO if user logged in, must return additional information for propositions that belong to user
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[User]] =>
            decodeRequest {
              entity(as[SearchRequest]) { request: SearchRequest =>
                provideAsync(
                  sequenceService
                    .search(userAuth.map(_.user.userId), request.toSearchQuery, requestContext)
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

  @ApiOperation(
    value = "search-all-sequences",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[IndexedSequence]]))
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
  @Path(value = "/search/all")
  def searchAll: Route = {
    post {
      path("sequences" / "search" / "all") {
        makeTrace("SearchAll") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[User] =>
            authorize(userAuth.user.roles.exists(role => role == RoleAdmin || role == RoleModerator)) {
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
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "sequenceId", paramType = "path", dataType = "string")))
  @Path(value = "/{sequenceId}/duplicates")
  def getDuplicates: Route = {
    get {
      path("sequences" / sequenceId / "duplicates") { sequenceId =>
        makeTrace("Duplicates") { requestContext =>
          makeOAuth2 { userAuth =>
            authorize(userAuth.user.roles.exists(role => role == RoleAdmin || role == RoleModerator)) {
              provideAsync(
                sequenceService.getDuplicates(userId = userAuth.user.userId, sequenceId = sequenceId, requestContext)
              ) { sequences =>
                complete(sequences)
              }
            }
          }
        }
      }
    }
  }
  @ApiOperation(
    value = "propose-sequence",
    httpMethod = "POST",
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.sequence.ProposeSequenceRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposeSequenceResponse]))
  )
  def postSequence: Route =
    post {
      path("sequences") {
        makeTrace("PostSequence") { requestContext =>
          makeOAuth2 { auth: AuthInfo[User] =>
            decodeRequest {
              entity(as[ProposeSequenceRequest]) { request: ProposeSequenceRequest =>
                onSuccess(
                  sequenceService
                    .propose(
                      user = auth.user,
                      requestContext = requestContext,
                      createdAt = DateHelper.now(),
                      content = request.content,
                      theme = request.theme
                    )
                ) { sequenceId =>
                  complete(StatusCodes.Created -> ProposeSequenceResponse(sequenceId))
                }
              }
            }
          }
        }
      }
    }

  @ApiOperation(
    value = "update-sequence",
    httpMethod = "PUT",
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.sequence.UpdateSequenceRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Sequence])))
  @Path(value = "/{sequenceId}")
  def updateSequence: Route =
    put {
      path("sequences" / sequenceId) { sequenceId =>
        makeTrace("EditSequence") { requestContext =>
          makeOAuth2 { userAuth: AuthInfo[User] =>
            decodeRequest {
              entity(as[UpdateSequenceRequest]) { request: UpdateSequenceRequest =>
                provideAsyncOrNotFound(sequenceService.getEventSourcingSequence(sequenceId, requestContext)) {
                  sequence =>
                    authorize(
                      sequence.author == userAuth.user.userId || userAuth.user.roles
                        .exists(role => role == RoleAdmin || role == RoleModerator)
                    ) {
                      onSuccess(
                        sequenceService
                          .update(
                            sequenceId = sequenceId,
                            requestContext = requestContext,
                            updatedAt = DateHelper.now(),
                            content = request.content
                          )
                      ) {
                        case Some(prop) => complete(prop)
                        case None       => complete(StatusCodes.Forbidden)
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
    value = "validate-sequence",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(new Authorization(value = "MakeApi"))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.sequence.ValidateSequenceRequest"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Sequence])))
  @Path(value = "/{sequenceId}/accept")
  def acceptSequence: Route = post {
    path("sequences" / sequenceId / "accept") { sequenceId =>
      makeTrace("ValidateSequence") { requestContext =>
        makeOAuth2 { auth: AuthInfo[User] =>
          requireModerationRole(auth.user) {
            decodeRequest {
              entity(as[ValidateSequenceRequest]) { request =>
                provideAsyncOrNotFound(
                  sequenceService.validateSequence(
                    sequenceId = sequenceId,
                    moderator = auth.user.userId,
                    requestContext = requestContext,
                    request = request
                  )
                ) { sequence: Sequence =>
                  complete(sequence)
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "refuse-sequence",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(new Authorization(value = "MakeApi"))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.sequence.RefuseSequenceRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Sequence])))
  @Path(value = "/{sequenceId}/refuse")
  def refuseSequence: Route = post {
    path("sequences" / sequenceId / "refuse") { sequenceId =>
      makeTrace("RefuseSequence") { requestContext =>
        makeOAuth2 { auth: AuthInfo[User] =>
          requireModerationRole(auth.user) {
            decodeRequest {
              entity(as[RefuseSequenceRequest]) { refuseSequenceRequest =>
                provideAsyncOrNotFound(
                  sequenceService.refuseSequence(
                    sequenceId = sequenceId,
                    moderator = auth.user.userId,
                    requestContext = requestContext,
                    request = refuseSequenceRequest
                  )
                ) { sequence: Sequence =>
                  complete(sequence)
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "vote-sequence", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "sequenceId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.sequence.VoteSequenceRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Vote])))
  @Path(value = "/{sequenceId}/vote")
  def vote: Route = post {
    path("sequences" / sequenceId / "vote") { sequenceId =>
      makeTrace("VoteSequence") { requestContext =>
        optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[User]] =>
          decodeRequest {
            entity(as[VoteSequenceRequest]) { request =>
              val maybeVoteKey = VoteKey.matchVoteKey(request.key)
              maybeVoteKey match {
                case None => complete(StatusCodes.BadRequest)
                case Some(voteKey) =>
                  provideAsync(
                    sequenceService.voteSequence(
                      sequenceId = sequenceId,
                      userId = maybeAuth.map(_.user.userId),
                      requestContext = requestContext,
                      voteKey = voteKey
                    )
                  ) {
                    case Some(vote) =>
                      complete(VoteResponse.parseVote(vote, maybeAuth.map(_.user.userId), requestContext.sessionId))
                    case None => complete(StatusCodes.NotFound)
                  }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "unvote-sequence", httpMethod = "POST", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "sequenceId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.sequence.VoteSequenceRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Vote])))
  @Path(value = "/{sequenceId}/unvote")
  def unvote: Route = post {
    path("sequences" / sequenceId / "unvote") { sequenceId =>
      makeTrace("UnvoteSequence") { requestContext =>
        optionalMakeOAuth2 { maybeAuth: Option[AuthInfo[User]] =>
          decodeRequest {
            entity(as[VoteSequenceRequest]) { request =>
              val maybeVoteKey = VoteKey.matchVoteKey(request.key)
              maybeVoteKey match {
                case None => complete(StatusCodes.BadRequest)
                case Some(voteKey) =>
                  provideAsync(
                    sequenceService.unvoteSequence(
                      sequenceId = sequenceId,
                      userId = maybeAuth.map(_.user.userId),
                      requestContext = requestContext,
                      voteKey = voteKey
                    )
                  ) {
                    case Some(vote) =>
                      complete(VoteResponse.parseVote(vote, maybeAuth.map(_.user.userId), requestContext.sessionId))
                    case None => complete(StatusCodes.NotFound)
                  }
              }
            }
          }
        }
      }
    }
  }
   */
  val sequenceRoutes: Route =
    startSequence ~
      getModerationSequence ~
      postSequence

  val sequenceId: PathMatcher1[SequenceId] =
    Segment.flatMap(id => Try(SequenceId(id)).toOption)

}

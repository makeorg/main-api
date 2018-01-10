package org.make.api.sequence

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, ReadJournalComponent}
import org.make.api.theme.ThemeServiceComponent
import org.make.core.auth.UserRights
import org.make.core.proposal.ProposalId
import org.make.core.sequence._
import org.make.core.sequence.indexed.{IndexedStartSequence, SequencesSearchResult}
import org.make.core.{DateHelper, HttpCodes, Validation}

import scala.concurrent.Future
import scalaoauth2.provider.AuthInfo

@Api(value = "Sequence")
@Path(value = "/")
trait SequenceApi extends MakeAuthenticationDirectives with StrictLogging {
  this: SequenceServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ThemeServiceComponent
    with TagServiceComponent
    with OperationServiceComponent
    with SequenceCoordinatorServiceComponent
    with ReadJournalComponent
    with ActorSystemComponent =>

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
          makeOAuth2 { auth: AuthInfo[UserRights] =>
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
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[CreateSequenceRequest]) { request: CreateSequenceRequest =>
                  provideAsync(themeService.findAll()) { themes =>
                    provideAsync(
                      request.operationId.map(operationService.findOne(_)).getOrElse(Future.successful(None))
                    ) { operation =>
                      val themeIds = request.themeIds.distinct
                      Validation.validate(
                        Validation
                          .validChoices("themeIds", Some("Some theme ids are invalid"), themeIds, themes.map(_.themeId))
                      )
                      onSuccess(
                        sequenceService
                          .create(
                            userId = auth.user.userId,
                            requestContext = requestContext,
                            createdAt = DateHelper.now(),
                            title = request.title,
                            tagIds = request.tagIds,
                            themeIds = themeIds,
                            operationId = operation.map(_.operationId),
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
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[UpdateSequenceRequest]) { request: UpdateSequenceRequest =>
                  provideAsync(themeService.findByIds(request.themeIds.getOrElse(Seq.empty))) { themes =>
                    provideAsync(tagService.findByTagIds(request.tagIds.getOrElse(Seq.empty))) { tags =>
                      provideAsync(
                        request.operation.map(operationService.findOne(_)).getOrElse(Future.successful(None))
                      ) { operation =>
                        val requestThemesSize: Int = request.themeIds.getOrElse(Seq.empty).distinct.size
                        Validation.validate(
                          Validation.validateEquals(
                            "themeIds",
                            Some("Some theme ids are invalid"),
                            requestThemesSize,
                            themes.size
                          )
                        )

                        Validation.validate(
                          Validation.validateEquals(
                            "tagIds",
                            Some("Some tag ids are invalid"),
                            request.tagIds.getOrElse(Seq.empty).distinct.size,
                            tags.size
                          )
                        )

                        if (request.status.nonEmpty) {
                          Validation.validate(
                            Validation
                              .validChoices(
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
                            status = request.status.map(SequenceStatus.statusMap),
                            operationId = operation.map(_.operationId),
                            themeIds = themes.map(_.themeId),
                            tagIds = tags.map(_.tagId)
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
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[AddProposalSequenceRequest]) { request: AddProposalSequenceRequest =>
                  provideAsyncOrNotFound(
                    sequenceService.addProposals(
                      sequenceId = sequenceId,
                      moderatorId = auth.user.userId,
                      requestContext = requestContext,
                      proposalIds = request.proposalIds
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
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[RemoveProposalSequenceRequest]) { request: RemoveProposalSequenceRequest =>
                  provideAsyncOrNotFound(
                    sequenceService.removeProposals(
                      sequenceId = sequenceId,
                      moderatorId = auth.user.userId,
                      requestContext = requestContext,
                      proposalIds = request.proposalIds
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
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "slug", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "include", paramType = "query", dataType = "string", allowMultiple = true)
    )
  )
  @Path(value = "/sequences/{slug}")
  def startSequenceBySlug: Route = {
    get {
      path("sequences" / sequenceSlug) { slug =>
        parameters('include.*) { (includes) =>
          makeTrace("Start Sequence by Slug") { requestContext =>
            optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
              decodeRequest {
                provideAsyncOrNotFound(
                  sequenceService
                    .startNewSequence(
                      maybeUserId = userAuth.map(_.user.userId),
                      slug = slug,
                      includedProposals = includes.toSeq.map(ProposalId(_)),
                      requestContext = requestContext
                    )
                ) { sequence =>
                  complete(sequence)
                }
              }

            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "start-sequence-by-id", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Option[IndexedStartSequence]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "id", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "include", paramType = "query", dataType = "string", allowMultiple = true)
    )
  )
  @Path(value = "/sequences/start/{id}")
  def startSequenceById: Route = {
    get {
      path("sequences" / "start" / sequenceId) { sequenceId =>
        parameters('include.*) { (includes) =>
          makeTrace("Start Sequence by Id") { requestContext =>
            optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
              decodeRequest {
                provideAsyncOrNotFound(
                  sequenceService
                    .startNewSequence(
                      maybeUserId = userAuth.map(_.user.userId),
                      sequenceId = sequenceId,
                      includedProposals = includes.toSeq.map(ProposalId(_)),
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
  }

  @ApiOperation(
    value = "update-sequences-operation",
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
  @Path(value = "/moderation/sequences/migrate-operation")
  def migrateSequenceOperation: Route = post {
    path("moderation" / "sequences" / "migrate-operation") {
      makeTrace("update sequence operation") { _ =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
          requireAdminRole(auth.user) {
            implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
            readJournal
              .currentPersistenceIds()
              .runForeach(id => sequenceCoordinatorService.setOperationIdFromContext(SequenceId(id)))

            complete(StatusCodes.NoContent)
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
      startSequenceById ~
      postAddProposalSequence ~
      postRemoveProposalSequence ~
      patchSequence ~
      migrateSequenceOperation

  val sequenceId: PathMatcher1[SequenceId] = Segment.map(id => SequenceId(id))
  val sequenceSlug: PathMatcher1[String] = Segment
}

package org.make.api.operation

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sequence.{SequenceResponse, SequenceServiceComponent}
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.UserServiceComponent
import org.make.core.auth.UserRights
import org.make.core.operation._
import org.make.core.reference.TagId
import org.make.core.sequence.SequenceId
import org.make.core.{reference, HttpCodes, Validation}

import scalaoauth2.provider.AuthInfo

@Api(value = "Moderation Operation")
@Path(value = "/moderation/operations")
trait ModerationOperationApi extends MakeAuthenticationDirectives with StrictLogging {
  this: OperationServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with OperationServiceComponent
    with SequenceServiceComponent
    with TagServiceComponent
    with UserServiceComponent =>

  @ApiOperation(value = "post-operation", httpMethod = "POST", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationId])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.operation.ModerationCreateOperationRequest"
      )
    )
  )
  @Path(value = "/")
  def moderationPostOperation: Route = {
    post {
      path("moderation" / "operations") {
        makeTrace("ModerationPostOperation") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[ModerationCreateOperationRequest]) { request: ModerationCreateOperationRequest =>
                  provideAsync(tagService.findByTagIds(request.countriesConfiguration.flatMap(_.tagIds))) { tags =>
                    provideAsync(sequenceService.getModerationSequenceById(request.sequenceLandingId)) { sequence =>
                      Validation.validate(
                        sequenceLandingIdValidation(request.sequenceLandingId, sequence),
                        tagsValidation(request.countriesConfiguration.flatMap(_.tagIds), tags)
                      )
                      provideAsync(operationService.findOneBySlug(request.slug)) { maybeOperation =>
                        Validation.validate(
                          Validation
                            .requireNotPresent("slug", maybeOperation, Some(s"Slug '${request.slug}' already exist"))
                        )
                        onSuccess(
                          operationService.create(
                            userId = auth.user.userId,
                            slug = request.slug,
                            translations = request.translations,
                            defaultLanguage = request.defaultLanguage,
                            sequenceLandingId = request.sequenceLandingId,
                            countriesConfiguration = request.countriesConfiguration
                          )
                        ) { operationId =>
                          complete(StatusCodes.Created -> Map("operationId" -> operationId))
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

  @ApiOperation(value = "put-operation", httpMethod = "PUT", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationId])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.operation.ModerationUpdateOperationRequest"
      ),
      new ApiImplicitParam(name = "operationId", paramType = "path", dataType = "string")
    )
  )
  @Path(value = "/{operationId}")
  def moderationPutOperation: Route = {
    put {
      path("moderation" / "operations" / operationId) { operationId =>
        makeTrace("ModerationPutOperation") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              provideAsyncOrNotFound(operationService.findOne(operationId)) { _ =>
                decodeRequest {
                  entity(as[ModerationUpdateOperationRequest]) { request: ModerationUpdateOperationRequest =>
                    provideAsync(tagService.findByTagIds(request.countriesConfiguration.flatMap(_.tagIds))) { tags =>
                      provideAsync(sequenceService.getModerationSequenceById(request.sequenceLandingId)) { sequence =>
                        Validation.validate(
                          sequenceLandingIdValidation(request.sequenceLandingId, sequence),
                          tagsValidation(request.countriesConfiguration.flatMap(_.tagIds), tags)
                        )
                        provideAsync(operationService.findOneBySlug(request.slug)) { maybeOperation =>
                          maybeOperation.foreach { operation =>
                            Validation.validate(
                              allowedSameSlugValidation(request.slug, operation.operationId.value, operationId.value)
                            )
                          }
                          onSuccess(
                            operationService.update(
                              operationId = operationId,
                              userId = auth.user.userId,
                              slug = Some(request.slug),
                              status = OperationStatus.statusMap.get(request.status),
                              translations = Some(request.translations),
                              defaultLanguage = Some(request.defaultLanguage),
                              sequenceLandingId = Some(request.sequenceLandingId),
                              countriesConfiguration = Some(request.countriesConfiguration)
                            )
                          ) { operationId =>
                            complete(StatusCodes.OK -> Map("operationId" -> operationId))
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

  private def allowedSameSlugValidation(slug: String, operationId: String, operationIdOfSlug: String) = {
    Validation
      .validateEquals("slug", Some(s"Slug '$slug' already exist"), operationId, operationIdOfSlug)
  }

  private def tagsValidation(tagIds: Seq[TagId], validTags: Seq[reference.Tag]) = {
    Validation.validateEquals("tagIds", Some("Some tag ids are invalid"), tagIds.distinct.size, validTags.size)
  }

  private def sequenceLandingIdValidation(sequenceLandingId: SequenceId, sequence: Option[SequenceResponse]) = {
    Validation.requirePresent(
      "sequenceLandingId",
      sequence,
      Some(s"Sequence with id '${sequenceLandingId.value}' not found")
    )
  }

  @ApiOperation(value = "get-operation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationOperationResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "operationId", paramType = "path", dataType = "string")))
  @Path(value = "/{operationId}")
  def moderationGetOperation: Route = {
    get {
      path("moderation" / "operations" / operationId) { operationId =>
        makeTrace("ModerationGetOperation") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              provideAsyncOrNotFound(operationService.findOne(operationId)) { operation =>
                provideAsync(userService.getUsersByUserIds(operation.events.map(_.makeUserId))) { users =>
                  complete(ModerationOperationResponse.apply(operation = operation, operationActionUsers = users))
                }
              }
            }
          }
        }
      }
    }
  }
  @ApiOperation(value = "get-operations", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationOperationListResponse]))
  )
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "slug", paramType = "query", required = false, dataType = "string"))
  )
  @Path(value = "/")
  def moderationGetOperations: Route = {
    get {
      path("moderation" / "operations") {
        parameters('slug.?) { (slug) =>
          makeTrace("ModerationGetOperations") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                provideAsync(operationService.find()) { operations =>
                  provideAsync(userService.getUsersByUserIds(operations.flatMap(_.events.map(_.makeUserId)).distinct)) {
                    users =>
                      val filteredOperation: Seq[Operation] = slug match {
                        case Some(value) => operations.filter(_.slug == value)
                        case _           => operations
                      }
                      val operationResponses: Seq[ModerationOperationResponse] =
                        filteredOperation.map(operation => ModerationOperationResponse(operation, users))
                      val result: ModerationOperationListResponse =
                        ModerationOperationListResponse(operationResponses.length, operationResponses)
                      complete(result)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  val moderationOperationRoutes: Route =
    moderationPostOperation ~ moderationGetOperation ~ moderationGetOperations ~ moderationPutOperation

  private val operationId: PathMatcher1[OperationId] = Segment.map(id => OperationId(id))
}

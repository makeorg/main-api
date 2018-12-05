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

package org.make.api.operation

import java.time.LocalDate

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sequence.{SequenceResponse, SequenceServiceComponent}
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.UserServiceComponent
import org.make.core.auth.UserRights
import org.make.core.operation._
import org.make.core.reference.Country
import org.make.core.sequence.SequenceId
import org.make.core.tag.TagId
import org.make.core.{tag, HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(
  value = "Moderation Operation",
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
@Path(value = "/moderation/operations")
trait ModerationOperationApi extends Directives {

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
  def moderationPostOperation: Route
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
  def moderationPutOperation: Route
  @ApiOperation(value = "get-operation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationOperationResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "operationId", paramType = "path", dataType = "string")))
  @Path(value = "/{operationId}")
  def moderationGetOperation: Route
  @ApiOperation(value = "get-operations", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ModerationOperationListResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "slug", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "openAt", paramType = "query", required = false, dataType = "date")
    )
  )
  @Path(value = "/")
  def moderationGetOperations: Route

  def routes: Route =
    moderationPostOperation ~ moderationGetOperation ~ moderationGetOperations ~ moderationPutOperation

  protected val operationId: PathMatcher1[OperationId] = Segment.map(id => OperationId(id))
}

trait ModerationOperationApiComponent {
  def moderationOperationApi: ModerationOperationApi
}

trait DefaultModerationOperationApiComponent
    extends ModerationOperationApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: OperationServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with OperationServiceComponent
    with SequenceServiceComponent
    with TagServiceComponent
    with UserServiceComponent =>

  override lazy val moderationOperationApi: ModerationOperationApi = new ModerationOperationApi {

    private def allowedSameSlugValidation(slug: String, operationId: String, operationIdOfSlug: String) = {
      Validation
        .validateEquals("slug", Some(s"Slug '$slug' already exist"), operationId, operationIdOfSlug)
    }

    private def tagsValidation(tagIds: Seq[TagId], validTags: Seq[tag.Tag]) = {
      Validation.validateEquals("tagIds", Some("Some tag ids are invalid"), tagIds.distinct.size, validTags.size)
    }

    private def sequenceLandingIdValidation(sequenceLandingId: SequenceId, sequence: Option[SequenceResponse]) = {
      Validation.requirePresent(
        "sequenceLandingId",
        sequence,
        Some(s"Sequence with id '${sequenceLandingId.value}' not found")
      )
    }

    def moderationPostOperation: Route = {
      post {
        path("moderation" / "operations") {
          makeOperation("ModerationPostOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                decodeRequest {
                  entity(as[ModerationCreateOperationRequest]) { request: ModerationCreateOperationRequest =>
                    provideAsync(tagService.findByTagIds(request.countriesConfiguration.flatMap(_.tagIds))) { tags =>
                      provideAsync(
                        Future.traverse(request.countriesConfiguration.map(_.landingSequenceId))(
                          sequenceService.getModerationSequenceById
                        )
                      ) { sequences =>
                        sequences.zipWithIndex.foreach {
                          case (sequence, idx) =>
                            Validation.validate(
                              sequenceLandingIdValidation(
                                request.countriesConfiguration(idx).landingSequenceId,
                                sequence
                              ),
                              tagsValidation(request.countriesConfiguration.flatMap(_.tagIds), tags)
                            )
                        }
                        provideAsync(operationService.findOneBySlug(request.slug)) { maybeOperation =>
                          Validation.validate(
                            Validation
                              .requireNotPresent("slug", maybeOperation, Some(s"Slug '${request.slug}' already exist"))
                          )
                          onSuccess(
                            operationService.create(
                              userId = auth.user.userId,
                              slug = request.slug,
                              defaultLanguage = request.defaultLanguage,
                              allowedSources = request.allowedSources
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

    def moderationPutOperation: Route = {
      put {
        path("moderation" / "operations" / operationId) { operationId =>
          makeOperation("ModerationPutOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                provideAsyncOrNotFound(operationService.findOne(operationId)) { _ =>
                  decodeRequest {
                    entity(as[ModerationUpdateOperationRequest]) { request: ModerationUpdateOperationRequest =>
                      provideAsync(tagService.findByTagIds(request.countriesConfiguration.flatMap(_.tagIds))) { tags =>
                        provideAsync(
                          Future.traverse(request.countriesConfiguration.map(_.landingSequenceId))(
                            sequenceService.getModerationSequenceById
                          )
                        ) { sequences =>
                          sequences.zipWithIndex.foreach {
                            case (sequence, idx) =>
                              Validation.validate(
                                sequenceLandingIdValidation(
                                  request.countriesConfiguration(idx).landingSequenceId,
                                  sequence
                                ),
                                tagsValidation(request.countriesConfiguration.flatMap(_.tagIds), tags)
                              )
                          }
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
                                defaultLanguage = Some(request.defaultLanguage),
                                allowedSources = Some(request.allowedSources)
                              )
                            ) {
                              case Some(id) => complete(StatusCodes.OK -> Map("operationId" -> id))
                              case None     => complete(StatusCodes.NotFound)
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

    def moderationGetOperation: Route = {
      get {
        path("moderation" / "operations" / operationId) { operationId =>
          makeOperation("ModerationGetOperation") { requestContext =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                provideAsyncOrNotFound(operationService.findOne(operationId)) { operation =>
                  provideAsync(userService.getUsersByUserIds(operation.events.map(_.makeUserId))) { users =>
                    provideAsync(tagService.findByQuestionIds(operation.questions.map(_.question.questionId))) { tags =>
                      complete(
                        ModerationOperationResponse
                          .apply(operation = operation, operationActionUsers = users, tags)
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

    def moderationGetOperations: Route = {
      get {
        path("moderation" / "operations") {
          parameters(('slug.?, 'country.as[Country].?, 'openAt.as[LocalDate].?)) { (slug, country, openAt) =>
            makeOperation("ModerationGetOperations") { requestContext =>
              makeOAuth2 { auth: AuthInfo[UserRights] =>
                requireModerationRole(auth.user) {
                  provideAsync(
                    operationService.find(slug = slug, country = country, maybeSource = None, openAt = openAt)
                  ) { operations =>
                    provideAsync(userService.getUsersByUserIds(operations.flatMap(_.events.map(_.makeUserId)).distinct)) {
                      users =>
                        provideAsync(
                          tagService.findByQuestionIds(operations.flatMap(_.questions).map(_.question.questionId))
                        ) { tags =>
                          val operationResponses: Seq[ModerationOperationResponse] =
                            operations.map(operation => ModerationOperationResponse(operation, users, tags))
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
    }
  }
}

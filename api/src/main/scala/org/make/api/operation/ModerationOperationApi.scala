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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sequence.SequenceServiceComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.api.user.UserServiceComponent
import org.make.core.auth.UserRights
import org.make.core.operation._
import org.make.core.{HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationIdResponse]))
  )
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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationIdResponse]))
  )
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
    value = Array(
      new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[ModerationOperationResponse]])
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "slug", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "operationKind", paramType = "query", required = false, dataType = "string")
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
    with SessionHistoryCoordinatorServiceComponent
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

    def moderationPostOperation: Route = {
      post {
        path("moderation" / "operations") {
          makeOperation("ModerationPostOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                decodeRequest {
                  entity(as[ModerationCreateOperationRequest]) { request: ModerationCreateOperationRequest =>
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
                          allowedSources = request.allowedSources,
                          operationKind = request.operationKind
                        )
                      ) { operationId =>
                        complete(StatusCodes.Created -> OperationIdResponse(operationId))
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
                provideAsyncOrNotFound(operationService.findOneSimple(operationId)) { _ =>
                  decodeRequest {
                    entity(as[ModerationUpdateOperationRequest]) { request: ModerationUpdateOperationRequest =>
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
                            allowedSources = Some(request.allowedSources),
                            operationKind = Some(request.operationKind)
                          )
                        ) {
                          case Some(id) => complete(StatusCodes.OK -> OperationIdResponse(id))
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

    def moderationGetOperation: Route = {
      get {
        path("moderation" / "operations" / operationId) { operationId =>
          makeOperation("ModerationGetOperation") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireModerationRole(auth.user) {
                provideAsyncOrNotFound(operationService.findOneSimple(operationId)) { operation =>
                  complete(ModerationOperationResponse.apply(operation = operation))
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
          makeOperation("ModerationGetOperations") { _ =>
            parameters(
              ('_start.as[Int].?, '_end.as[Int].?, '_sort.?, '_order.?, 'slug.?, 'operationKind.as[OperationKind].?)
            ) {
              (start: Option[Int],
               end: Option[Int],
               sort: Option[String],
               order: Option[String],
               slug: Option[String],
               operationKind: Option[OperationKind]) =>
                makeOAuth2 { auth: AuthInfo[UserRights] =>
                  requireModerationRole(auth.user) {
                    order.foreach { orderValue =>
                      Validation.validate(
                        Validation
                          .validChoices(
                            "_order",
                            Some("Invalid order"),
                            Seq(orderValue.toLowerCase),
                            Seq("desc", "asc")
                          )
                      )
                    }
                    provideAsync(operationService.count(slug = slug, operationKind = operationKind)) { count =>
                      provideAsync(
                        operationService
                          .findSimple(
                            start = start.getOrElse(0),
                            end = end,
                            sort = sort,
                            order = order,
                            slug = slug,
                            operationKind = operationKind
                          )
                      ) { operations =>
                        val operationResponses: Seq[ModerationOperationResponse] =
                          operations.map(operation => ModerationOperationResponse(operation))
                        complete((StatusCodes.OK, List(TotalCountHeader(count.toString)), operationResponses))
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

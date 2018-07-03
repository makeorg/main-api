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

package org.make.api.tagtype

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.auth.UserRights
import org.make.core.tag.{TagTypeDisplay, TagTypeId}
import org.make.core.{tag, HttpCodes}
import scalaoauth2.provider.AuthInfo

import scala.util.Try

@Api(value = "Moderation Tag Types")
@Path(value = "/moderation/tag-types")
trait ModerationTagTypeApi extends MakeAuthenticationDirectives {
  this: TagTypeServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  @Path(value = "/{tagTypeId}")
  @ApiOperation(
    value = "get-tag-type",
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[tag.TagType])))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "tagTypeId", paramType = "path", dataType = "string")))
  def moderationGetTagType: Route = {
    get {
      path("moderation" / "tag-types" / moderationTagTypeId) { tagTypeId =>
        makeOperation("ModerationGetTagType") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              provideAsyncOrNotFound(tagTypeService.getTagType(tagTypeId)) { tagType =>
                complete(TagTypeResponse(tagType))
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "create-tag-type",
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.tagtype.CreateTagTypeRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[tag.TagType])))
  @Path(value = "/")
  def moderationCreateTagType: Route = post {
    path("moderation" / "tag-types") {
      makeOperation("ModerationCreateTagType") { _ =>
        makeOAuth2 { userAuth: AuthInfo[UserRights] =>
          requireModerationRole(userAuth.user) {
            decodeRequest {
              entity(as[CreateTagTypeRequest]) { request: CreateTagTypeRequest =>
                onSuccess(tagTypeService.createTagType(request.label, request.display, request.weight)) { tagType =>
                  complete(StatusCodes.Created -> TagTypeResponse(tagType))
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "update-tag-type",
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
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.tagtype.UpdateTagTypeRequest"),
      new ApiImplicitParam(name = "tagTypeId", paramType = "path", dataType = "string")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[tag.TagType])))
  @Path(value = "/{tagTypeId}")
  def moderationUpdateTagType: Route = put {
    path("moderation" / "tag-types" / moderationTagTypeId) { moderationTagTypeId =>
      makeOperation("ModerationRegisterTagType") { _ =>
        makeOAuth2 { userAuth: AuthInfo[UserRights] =>
          requireModerationRole(userAuth.user) {
            decodeRequest {
              entity(as[UpdateTagTypeRequest]) { request: UpdateTagTypeRequest =>
                provideAsyncOrNotFound(
                  tagTypeService.updateTagType(moderationTagTypeId, request.label, request.display, request.weight)
                ) { tagType =>
                  complete(TagTypeResponse(tagType))
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "list-tag-types",
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "label", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[TagTypeResponse]]))
  )
  @Path(value = "/")
  def moderationListTagTypes: Route = {
    get {
      path("moderation" / "tag-types") {
        makeOperation("ModerationSearchTagType") { _ =>
          parameters(('_start.as[Int].?, '_end.as[Int].?, '_sort.?, '_order.?, 'label.?)) {
            (start, end, sort, order, label_filter) =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireModerationRole(userAuth.user) {
                  onSuccess(tagTypeService.findAll()) { tagTypes =>
                    //TODO: define the sort in the persistence layer
                    val filteredTagTypes = tagTypes.filter(t => label_filter.forall(t.label.contains(_)))
                    complete(
                      (
                        StatusCodes.OK,
                        List(TotalCountHeader(filteredTagTypes.size.toString)),
                        filteredTagTypes.slice(start.getOrElse(0), end.getOrElse(10)).map(TagTypeResponse.apply)
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

  val moderationTagTypeRoutes
    : Route = moderationGetTagType ~ moderationCreateTagType ~ moderationUpdateTagType ~ moderationListTagTypes

  val moderationTagTypeId: PathMatcher1[TagTypeId] =
    Segment.flatMap(id => Try(TagTypeId(id)).toOption)
}

case class CreateTagTypeRequest(label: String, display: TagTypeDisplay, weight: Int)

object CreateTagTypeRequest {
  implicit val decoder: Decoder[CreateTagTypeRequest] = deriveDecoder[CreateTagTypeRequest]
}

case class UpdateTagTypeRequest(label: String, display: TagTypeDisplay, weight: Int)

object UpdateTagTypeRequest {
  implicit val decoder: Decoder[UpdateTagTypeRequest] = deriveDecoder[UpdateTagTypeRequest]
}

package org.make.api.tag

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId
import org.make.core.tag.{TagDisplay, TagId, TagTypeId}
import org.make.core.{tag, HttpCodes}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Tags")
@Path(value = "/tags")
trait TagApi extends MakeAuthenticationDirectives {
  this: TagServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  @Path(value = "/{tagId}")
  @ApiOperation(value = "get-tag", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[tag.Tag])))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "tagId", paramType = "path", dataType = "string")))
  def getTag: Route = {
    get {
      path("tags" / tagId) { tagId =>
        makeOperation("GetTag") { _ =>
          provideAsyncOrNotFound(tagService.getTag(tagId)) { tag =>
            complete(tag)
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "create-tag",
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
    value =
      Array(new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.tag.CreateTagRequest"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[tag.Tag])))
  @Path(value = "/")
  def create: Route = post {
    path("tags") {
      makeOperation("RegisterTag") { _ =>
        makeOAuth2 { userAuth: AuthInfo[UserRights] =>
          requireModerationRole(userAuth.user) {
            decodeRequest {
              entity(as[CreateTagRequest]) { request: CreateTagRequest =>
                onSuccess(
                  tagService.createTag(
                    label = request.label,
                    tagTypeId = request.tagTypeId,
                    operationId = request.operationId,
                    themeId = request.themeId,
                    country = request.country,
                    language = request.language,
                    display = request.display.getOrElse(TagDisplay.Inherit)
                  )
                ) { tag =>
                  complete(StatusCodes.Created -> tag)
                }
              }
            }
          }
        }
      }
    }
  }

  @ApiOperation(value = "list-tags", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[tag.Tag]])))
  @Path(value = "/")
  def listTags: Route = {
    get {
      path("tags") {
        makeOperation("Search") { _ =>
          onSuccess(tagService.findAll()) { tags =>
            complete(tags)
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "update-tag",
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
      new ApiImplicitParam(name = "tagId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.tag.UpdateTagRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[tag.Tag])))
  @Path(value = "/{tagId}")
  def updateTag: Route = put {
    path("tags" / tagId) { tagId =>
      makeOperation("UpdateTag") { _ =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
          requireModerationRole(auth.user) {
            decodeRequest {
              entity(as[UpdateTagRequest]) { request: UpdateTagRequest =>
                provideAsyncOrNotFound(tagService.getTag(tagId)) { maybeOldTag =>
                  provideAsyncOrNotFound(
                    tagService.updateTag(
                      tagId = tagId,
                      label = request.label,
                      display = maybeOldTag.display,
                      tagTypeId = maybeOldTag.tagTypeId,
                      weight = maybeOldTag.weight,
                      operationId = maybeOldTag.operationId,
                      themeId = maybeOldTag.themeId,
                      country = maybeOldTag.country,
                      language = maybeOldTag.language
                    )
                  ) { tag =>
                    complete(tag)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  val tagRoutes: Route = getTag ~ create ~ listTags ~ updateTag

  val tagId: PathMatcher1[TagId] =
    Segment.flatMap(id => Try(TagId(id)).toOption)
}

case class CreateTagRequest(label: String,
                            tagTypeId: TagTypeId,
                            operationId: Option[OperationId],
                            themeId: Option[ThemeId],
                            country: String,
                            language: String,
                            display: Option[TagDisplay])

object CreateTagRequest {
  implicit val decoder: Decoder[CreateTagRequest] = deriveDecoder[CreateTagRequest]
}

case class UpdateTagRequest(label: String,
                            tagTypeId: TagTypeId,
                            operationId: Option[OperationId],
                            themeId: Option[ThemeId],
                            country: String,
                            language: String,
                            display: Option[TagDisplay])

object UpdateTagRequest {
  implicit val decoder: Decoder[UpdateTagRequest] = deriveDecoder[UpdateTagRequest]
}

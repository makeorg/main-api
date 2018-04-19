package org.make.api.tag

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.tag.TagId
import org.make.core.{tag, HttpCodes, Validation}
import scalaoauth2.provider.AuthInfo

import scala.util.Try

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
                onSuccess(tagService.createLegacyTag(request.label)) { tag =>
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
      makeOperation("UpdateTag") { requestContext =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
          requireAdminRole(auth.user) {
            decodeRequest {
              entity(as[UpdateTagRequest]) { request: UpdateTagRequest =>
                provideAsyncOrNotFound(tagService.getTag(tagId)) { maybeOldTag =>
                  provideAsync(tagService.getTag(tagId)) { maybeNewTag =>
                    Validation.validate(
                      Validation.requireNotPresent(
                        fieldName = "label",
                        fieldValue = maybeNewTag,
                        message = Some("New tag already exist. Duplicates are not allowed")
                      )
                    )

                    provideAsyncOrNotFound(
                      tagService.updateTag(
                        slug = maybeOldTag.tagId,
                        newTagLabel = request.label,
                        requestContext = requestContext,
                        connectedUserId = Some(auth.user.userId)
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
  }

  val tagRoutes: Route = getTag ~ create ~ listTags ~ updateTag

  val tagId: PathMatcher1[TagId] =
    Segment.flatMap(id => Try(TagId(id)).toOption)
}

case class CreateTagRequest(label: String)

object CreateTagRequest {
  implicit val decoder: Decoder[CreateTagRequest] = deriveDecoder[CreateTagRequest]
}

case class UpdateTagRequest(label: String)

object UpdateTagRequest {
  implicit val decoder: Decoder[UpdateTagRequest] = deriveDecoder[UpdateTagRequest]
}

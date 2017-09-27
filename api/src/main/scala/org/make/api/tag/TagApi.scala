package org.make.api.tag

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.reference.{Tag, TagId}
import org.make.core.user.Role.{RoleAdmin, RoleModerator}
import org.make.core.user.User

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Tag")
@Path(value = "/")
trait TagApi extends MakeAuthenticationDirectives {
  this: TagServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  @Path(value = "/tags/{tagId}")
  @ApiOperation(value = "get-tag", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Tag])))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "tagId", paramType = "path", dataType = "string")))
  def getTag: Route = {
    get {
      path("tags" / tagId) { tagId =>
        makeTrace("GetTag") { _ =>
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Tag])))
  @Path(value = "/tag")
  def create: Route = post {
    path("tag") {
      makeTrace("RegisterTag") { _ =>
        makeOAuth2 { userAuth: AuthInfo[User] =>
          authorize(userAuth.user.roles.exists(role => role == RoleAdmin || role == RoleModerator)) {
            decodeRequest {
              entity(as[CreateTagRequest]) { request: CreateTagRequest =>
                onSuccess(tagService.createTag(request.label)) { tag =>
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[Tag]])))
  @Path(value = "/tags")
  def listTags: Route = {
    get {
      path("tags") {
        makeTrace("Search") { _ =>
          onSuccess(tagService.findAll()) { tags =>
            complete(tags)
          }
        }
      }
    }
  }

  val tagRoutes: Route = getTag ~ create ~ listTags

  val tagId: PathMatcher1[TagId] =
    Segment.flatMap(id => Try(TagId(id)).toOption)
}

case class CreateTagRequest(label: String)
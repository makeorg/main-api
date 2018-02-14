package org.make.api.tag

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server._
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import org.make.core.reference.{Tag, TagId}
import org.make.core.user.Role.{RoleAdmin, RoleModerator}

import scala.util.Try
import scalaoauth2.provider.AuthInfo

@Api(value = "Moderation Tags")
@Path(value = "/moderation/tags")
trait ModerationTagApi extends MakeAuthenticationDirectives {
  this: TagServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  @Path(value = "/{tagId}")
  @ApiOperation(
    value = "get-tag",
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Tag])))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "tagId", paramType = "path", dataType = "string")))
  def moderationGetTag: Route = {
    get {
      path("moderation" / "tags" / moderationTagId) { tagId =>
        makeTrace("GetTag") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            authorize(userAuth.user.roles.exists(role => role == RoleAdmin || role == RoleModerator)) {
              provideAsyncOrNotFound(tagService.getTag(tagId)) { tag =>
                complete(tag)
              }
            }
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
  @Path(value = "/")
  def moderationCreateTag: Route = post {
    path("moderation" / "tags") {
      makeTrace("RegisterTag") { _ =>
        makeOAuth2 { userAuth: AuthInfo[UserRights] =>
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

  @ApiOperation(
    value = "list-tags",
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
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[TagResponse]]))
  )
  @Path(value = "/")
  def moderationlistTags: Route = {
    get {
      path("moderation" / "tags") {
        makeTrace("Search") { _ =>
          parameters(('_start.as[Int], '_end.as[Int], '_sort, '_order)) { (start, end, sort, order) =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              authorize(userAuth.user.roles.exists(role => role == RoleAdmin || role == RoleModerator)) {
                onSuccess(tagService.findAllEnabled()) { tags =>
                  val sortField =
                    try {
                      classOf[Tag].getDeclaredField(sort)
                    } catch {
                      case _: Throwable => classOf[Tag].getDeclaredField("label")
                    }
                  sortField.setAccessible(true)
                  val cmp = (a: Object, b: Object, order: String) => {
                    if (order == "ASC") a.toString < b.toString else a.toString > b.toString
                  }
                  complete(
                    (
                      StatusCodes.OK,
                      List(RawHeader("x-total-count", tags.length.toString)),
                      tags
                        .sortWith((_, _) => cmp(sortField.get(_), sortField.get(_), order))
                        .slice(start, end)
                        .map(TagResponse.apply)
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

  val moderationTagRoutes: Route = moderationGetTag ~ moderationCreateTag ~ moderationlistTags

  val moderationTagId: PathMatcher1[TagId] =
    Segment.flatMap(id => Try(TagId(id)).toOption)
}

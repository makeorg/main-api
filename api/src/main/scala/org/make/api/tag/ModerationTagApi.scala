package org.make.api.tag

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.auth.UserRights
import org.make.core.tag.TagId
import org.make.core.tag.Tag
import org.make.core.HttpCodes
import scalaoauth2.provider.AuthInfo

import scala.util.Try

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
        makeOperation("ModerationGetTag") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              provideAsyncOrNotFound(tagService.getTag(tagId)) { tag =>
                complete(TagResponse(tag))
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
      makeOperation("ModerationRegisterTag") { _ =>
        makeOAuth2 { userAuth: AuthInfo[UserRights] =>
          requireModerationRole(userAuth.user) {
            decodeRequest {
              entity(as[CreateTagRequest]) { request: CreateTagRequest =>
                onSuccess(tagService.createLegacyTag(request.label)) { tag =>
                  complete(StatusCodes.Created -> TagResponse(tag))
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
        makeOperation("ModerationSearchTag") { _ =>
          parameters(('_start.as[Int], '_end.as[Int], '_sort, '_order, 'label.?)) {
            (start, end, sort, order, label_filter) =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireModerationRole(userAuth.user) {
                  onSuccess(tagService.findAll()) { tags =>
                    val sortField =
                      try {
                        classOf[Tag].getDeclaredField(sort)
                      } catch {
                        case _: Throwable => classOf[Tag].getDeclaredField("label")
                      }
                    sortField.setAccessible(true)
                    val cmp = (a: Object, b: Object, order: String) => {
                      if (order == "DESC") a.toString < b.toString else a.toString > b.toString
                    }
                    val filteredTags = tags
                      .filter(t => label_filter.forall(t.label.contains(_)))
                      .sortWith((_, _) => cmp(sortField.get(_), sortField.get(_), order))
                    complete(
                      (
                        StatusCodes.OK,
                        List(TotalCountHeader(filteredTags.size.toString)),
                        filteredTags
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

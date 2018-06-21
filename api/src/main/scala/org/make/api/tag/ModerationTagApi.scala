package org.make.api.tag

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.{HttpCodes, Validation}
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
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[TagResponse])))
  @Path(value = "/")
  def moderationCreateTag: Route = post {
    path("moderation" / "tags") {
      makeOperation("ModerationRegisterTag") { _ =>
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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string", required = true),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string", required = true),
      new ApiImplicitParam(name = "_sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "label", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "tagTypeId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "operationId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "themeId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string")
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
          parameters(
            (
              '_start.as[Int].?,
              '_end.as[Int].?,
              '_sort.?,
              '_order.?,
              'label.?,
              'tagTypeId.?,
              'operationId.?,
              'themeId.?,
              'country.?,
              'language.?
            )
          ) {
            (start,
             end,
             sort,
             order,
             maybeLabel,
             maybeTagTypeId,
             maybeOperationId,
             maybeThemeId,
             maybeCountry,
             maybeLanguage) =>
              makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                requireModerationRole(userAuth.user) {

                  sort.foreach { sortValue =>
                    Validation.validate(
                      Validation.validChoices("_sort", Some("Invalid sort"), Seq(sortValue), Seq("label"))
                    )
                  }
                  order.foreach { orderValue =>
                    Validation.validate(
                      Validation
                        .validChoices("_order", Some("Invalid order"), Seq(orderValue.toLowerCase), Seq("desc", "asc"))
                    )
                  }
                  maybeCountry.foreach { country =>
                    Validation.validate(
                      Validation.validMatch("country", country, Some("Invalid country"), "^[a-zA-Z]{2,3}$".r)
                    )
                  }
                  maybeLanguage.foreach { language =>
                    Validation.validate(
                      Validation.validMatch("language", language, Some("Invalid language"), "^[a-zA-Z]{2,3}$".r)
                    )
                  }

                  onSuccess(
                    tagService.search(
                      start.getOrElse(0),
                      end,
                      sort,
                      order,
                      TagFilter(
                        label = maybeLabel,
                        tagTypeId = maybeTagTypeId.map(TagTypeId(_)),
                        operationId = maybeOperationId.map(OperationId(_)),
                        themeId = maybeThemeId.map(ThemeId(_)),
                        country = maybeCountry,
                        language = maybeLanguage
                      )
                    )
                  ) { filteredTags =>
                    complete(
                      (
                        StatusCodes.OK,
                        List(TotalCountHeader(filteredTags.size.toString)),
                        filteredTags.map(TagResponse.apply)
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

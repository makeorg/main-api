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

package org.make.api.tag

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
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.{tag, HttpCodes, Validation}
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
                provideAsync(tagService.searchByLabel(request.label, like = false)) { tagList =>
                  val duplicateLabel = tagList.find { tag =>
                    (tag.operationId.isDefined && tag.operationId == request.operationId) ||
                    (tag.themeId.isDefined && tag.themeId == request.themeId)
                  }
                  Validation.validate(
                    Validation.requireNotPresent(
                      fieldName = "label",
                      fieldValue = duplicateLabel,
                      message = Some("Tag label already exist in this context. Duplicates are not allowed")
                    )
                  )
                  Validation.validate(
                    Validation.requirePresent(
                      fieldName = "operationId / themeId",
                      fieldValue = request.operationId.orElse(request.themeId),
                      message = Some("operation or theme should not be empty")
                    )
                  )
                  if (request.operationId.nonEmpty) {
                    Validation.validate(
                      Validation.requireNotPresent(
                        fieldName = "themeId",
                        fieldValue = request.themeId,
                        message = Some("Tag can not have both operation and theme")
                      )
                    )
                  }
                  if (request.themeId.nonEmpty) {
                    Validation.validate(
                      Validation.requireNotPresent(
                        fieldName = "operationId",
                        fieldValue = request.operationId,
                        message = Some("Tag can not have both operation and theme")
                      )
                    )
                  }
                  onSuccess(
                    tagService.createTag(
                      label = request.label,
                      tagTypeId = request.tagTypeId,
                      operationId = request.operationId,
                      themeId = request.themeId,
                      country = request.country,
                      language = request.language,
                      display = request.display.getOrElse(TagDisplay.Inherit),
                      weight = request.weight.getOrElse(0f)
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
      new ApiImplicitParam(name = "_start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "_end", paramType = "query", dataType = "string"),
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

                  provideAsync(
                    tagService.count(
                      TagFilter(
                        label = maybeLabel,
                        tagTypeId = maybeTagTypeId.map(TagTypeId(_)),
                        operationId = maybeOperationId.map(OperationId(_)),
                        themeId = maybeThemeId.map(ThemeId(_)),
                        country = maybeCountry,
                        language = maybeLanguage
                      )
                    )
                  ) { count =>
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
                        (StatusCodes.OK, List(TotalCountHeader(count.toString)), filteredTags.map(TagResponse.apply))
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
  def moderationUpdateTag: Route = put {
    path("moderation" / "tags" / moderationTagId) { tagId =>
      makeOperation("ModerationUpdateTag") { _ =>
        makeOAuth2 { auth: AuthInfo[UserRights] =>
          requireModerationRole(auth.user) {
            decodeRequest {
              entity(as[UpdateTagRequest]) { request: UpdateTagRequest =>
                provideAsync(tagService.searchByLabel(request.label, like = false)) { tagList =>
                  val duplicateLabel = tagList.find { tag =>
                    (tag.tagId != tagId) &&
                    (tag.operationId.isDefined && tag.operationId == request.operationId) ||
                    (tag.themeId.isDefined && tag.themeId == request.themeId)
                  }
                  Validation.validate(
                    Validation.requireNotPresent(
                      fieldName = "label",
                      fieldValue = duplicateLabel,
                      message = Some("Tag label already exist in this context. Duplicates are not allowed")
                    )
                  )
                  Validation.validate(
                    Validation.requirePresent(
                      fieldName = "operation/theme",
                      fieldValue = request.operationId.orElse(request.themeId),
                      message = Some("operation or theme should not be empty")
                    )
                  )
                  if (request.operationId.nonEmpty) {
                    Validation.validate(
                      Validation.requireNotPresent(
                        fieldName = "theme",
                        fieldValue = request.themeId,
                        message = Some("Tag can not have both operation and theme")
                      )
                    )
                  }
                  if (request.themeId.nonEmpty) {
                    Validation.validate(
                      Validation.requireNotPresent(
                        fieldName = "operation",
                        fieldValue = request.operationId,
                        message = Some("Tag can not have both operation and theme")
                      )
                    )
                  }
                  provideAsyncOrNotFound(
                    tagService.updateTag(
                      tagId = tagId,
                      label = request.label,
                      display = request.display,
                      tagTypeId = request.tagTypeId,
                      weight = request.weight,
                      operationId = request.operationId,
                      themeId = request.themeId,
                      country = request.country,
                      language = request.language
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

  val moderationTagRoutes: Route = moderationGetTag ~ moderationCreateTag ~ moderationlistTags ~ moderationUpdateTag

  val moderationTagId: PathMatcher1[TagId] =
    Segment.flatMap(id => Try(TagId(id)).toOption)
}

case class CreateTagRequest(label: String,
                            tagTypeId: TagTypeId,
                            operationId: Option[OperationId],
                            themeId: Option[ThemeId],
                            country: String,
                            language: String,
                            display: Option[TagDisplay],
                            weight: Option[Float])

object CreateTagRequest {
  implicit val decoder: Decoder[CreateTagRequest] = deriveDecoder[CreateTagRequest]
}

case class UpdateTagRequest(label: String,
                            tagTypeId: TagTypeId,
                            operationId: Option[OperationId],
                            themeId: Option[ThemeId],
                            country: String,
                            language: String,
                            display: TagDisplay,
                            weight: Float)

object UpdateTagRequest {
  implicit val decoder: Decoder[UpdateTagRequest] = deriveDecoder[UpdateTagRequest]
}

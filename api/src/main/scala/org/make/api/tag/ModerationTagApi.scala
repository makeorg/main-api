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
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.auth.UserRights
import org.make.core.question.QuestionId
import org.make.core.tag.{Tag, TagDisplay, TagId, TagTypeId}
import org.make.core.{tag, HttpCodes, ParameterExtractors, Validation}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.Future
import scala.util.Try

@Api(value = "Moderation Tags")
@Path(value = "/moderation/tags")
trait ModerationTagApi extends Directives {

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
  def moderationGetTag: Route

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
  def moderationCreateTag: Route

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
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[TagResponse]]))
  )
  @Path(value = "/")
  def moderationlistTags: Route

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
  def moderationUpdateTag: Route

  def routes: Route = moderationGetTag ~ moderationCreateTag ~ moderationlistTags ~ moderationUpdateTag
}

trait ModerationTagApiComponent {
  def moderationTagApi: ModerationTagApi
}

trait DefaultModerationTagApiComponent
    extends ModerationTagApiComponent
    with MakeAuthenticationDirectives
    with ParameterExtractors {
  this: TagServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with QuestionServiceComponent =>

  val moderationTagId: PathMatcher1[TagId] = Segment.flatMap(id => Try(TagId(id)).toOption)

  override lazy val moderationTagApi: ModerationTagApi =
    new ModerationTagApi {
      override def moderationGetTag: Route = {
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

      override def moderationCreateTag: Route = post {
        path("moderation" / "tags") {
          makeOperation("ModerationRegisterTag") { _ =>
            makeOAuth2 { userAuth: AuthInfo[UserRights] =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[CreateTagRequest]) { request: CreateTagRequest =>
                    provideAsync(tagService.findByLabel(request.label, like = false)) { tagList =>
                      val duplicateLabel = tagList.find { tag =>
                        tag.questionId.isDefined && tag.questionId == request.questionId
                      }
                      Validation.validate(
                        Validation.requireNotPresent(
                          fieldName = "label",
                          fieldValue = duplicateLabel,
                          message = Some("Tag label already exist in this context. Duplicates are not allowed")
                        )
                      )
                      provideAsyncOrNotFound {
                        request.questionId.map { questionId =>
                          questionService.getQuestion(questionId)
                        }.getOrElse(Future.successful(None))
                      } { question =>
                        onSuccess(
                          tagService.createTag(
                            label = request.label,
                            tagTypeId = request.tagTypeId,
                            question = question,
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
      }

      override def moderationlistTags: Route = {
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
                  'tagTypeId.as[TagTypeId].?,
                  'questionId.as[QuestionId].?
                )
              ) {
                (start: Option[Int],
                 end: Option[Int],
                 sort: Option[String],
                 order: Option[String],
                 maybeLabel: Option[String],
                 maybeTagTypeId: Option[TagTypeId],
                 maybeQuestionId: Option[QuestionId]) =>
                  makeOAuth2 { userAuth: AuthInfo[UserRights] =>
                    requireModerationRole(userAuth.user) {

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
                      provideAsync(
                        tagService
                          .count(
                            TagFilter(label = maybeLabel, tagTypeId = maybeTagTypeId, questionId = maybeQuestionId)
                          )
                      ) { count =>
                        onSuccess(
                          tagService.find(
                            start = start.getOrElse(0),
                            end = end,
                            sort = sort,
                            order = order,
                            tagFilter =
                              TagFilter(label = maybeLabel, tagTypeId = maybeTagTypeId, questionId = maybeQuestionId)
                          )
                        ) { filteredTags =>
                          complete(
                            (
                              StatusCodes.OK,
                              List(TotalCountHeader(count.toString)),
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
      }

      override def moderationUpdateTag: Route = put {
        path("moderation" / "tags" / moderationTagId) { tagId =>
          makeOperation("ModerationUpdateTag") { requestContext =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                decodeRequest {
                  entity(as[UpdateTagRequest]) { request: UpdateTagRequest =>
                    provideAsync(tagService.findByLabel(request.label, like = false)) { tagList =>
                      val duplicateLabel = tagList.find { tag =>
                        tag.tagId != tagId && tag.questionId.isDefined && tag.questionId == request.questionId
                      }
                      Validation.validate(
                        Validation.requireNotPresent(
                          fieldName = "label",
                          fieldValue = duplicateLabel,
                          message = Some("Tag label already exist in this context. Duplicates are not allowed")
                        )
                      )
                      provideAsyncOrNotFound(
                        request.questionId
                          .map(questionId => questionService.getQuestion(questionId))
                          .getOrElse(Future.successful(None))
                      ) { question =>
                        provideAsyncOrNotFound(
                          tagService.updateTag(
                            tagId = tagId,
                            label = request.label,
                            display = request.display,
                            tagTypeId = request.tagTypeId,
                            weight = request.weight,
                            question = question,
                            requestContext = requestContext
                          )
                        ) { tag =>
                          complete(TagResponse(tag))
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

case class CreateTagRequest(label: String,
                            tagTypeId: TagTypeId,
                            questionId: Option[QuestionId],
                            display: Option[TagDisplay],
                            weight: Option[Float]) {
  Validation.validate(
    Validation
      .requirePresent(fieldName = "question", fieldValue = questionId, message = Some("question should not be empty"))
  )
}

object CreateTagRequest {
  implicit val decoder: Decoder[CreateTagRequest] = deriveDecoder[CreateTagRequest]
}

case class UpdateTagRequest(label: String,
                            tagTypeId: TagTypeId,
                            questionId: Option[QuestionId],
                            display: TagDisplay,
                            weight: Float) {
  Validation.validate(
    Validation
      .requirePresent(fieldName = "question", fieldValue = questionId, message = Some("question should not be empty"))
  )
}

object UpdateTagRequest {
  implicit val decoder: Decoder[UpdateTagRequest] = deriveDecoder[UpdateTagRequest]
}

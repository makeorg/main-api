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

import akka.http.scaladsl.server._
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.{tag, HttpCodes, ParameterExtractors, Validation}

import scala.concurrent.Future
import scala.util.Try

@Api(value = "Tags")
@Path(value = "/tags")
trait TagApi extends Directives {

  @Path(value = "/{tagId}")
  @ApiOperation(value = "get-tag", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[tag.Tag])))
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "tagId", paramType = "path", dataType = "string")))
  def getTag: Route

  @ApiOperation(value = "list-tags", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "operationId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "questionId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "themeId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[tag.Tag]])))
  @Path(value = "/")
  def listTags: Route

  final def routes: Route = getTag ~ listTags

  val tagId: PathMatcher1[TagId] =
    Segment.flatMap(id => Try(TagId(id)).toOption)
}

trait TagApiComponent {
  def tagApi: TagApi
}

trait DefaultTagApiComponent extends TagApiComponent with MakeAuthenticationDirectives with ParameterExtractors {
  this: TagServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with QuestionServiceComponent =>
  override lazy val tagApi: TagApi = new TagApi {
    override def getTag: Route = get {
      path("tags" / tagId) { tagId =>
        makeOperation("GetTag") { _ =>
          provideAsyncOrNotFound(tagService.getTag(tagId)) { tag =>
            complete(tag)
          }
        }
      }
    }

    override def listTags: Route = get {
      path("tags") {
        makeOperation("Search") { _ =>
          parameters(
            (
              'start.as[Int].?,
              'end.as[Int].?,
              'operationId.as[OperationId].?,
              'questionId.as[QuestionId].?,
              'themeId.as[ThemeId].?,
              'country.as[Country].?,
              'language.as[Language].?
            )
          ) { (start, end, maybeOperationId, maybeQuestionId, maybeThemeId, maybeCountry, maybeLanguage) =>
            maybeCountry.foreach { country =>
              Validation.validate(
                Validation.validMatch("country", country.value, Some("Invalid country"), "^[a-zA-Z]{2,3}$".r)
              )
            }
            maybeLanguage.foreach { language =>
              Validation.validate(
                Validation.validMatch("language", language.value, Some("Invalid language"), "^[a-zA-Z]{2,3}$".r)
              )
            }
            provideAsync {
              (for {
                country  <- maybeCountry
                language <- maybeLanguage
              } yield {
                questionService.findQuestionByQuestionIdOrThemeOrOperation(
                  maybeQuestionId,
                  maybeThemeId,
                  maybeOperationId,
                  country,
                  language
                )
              }).getOrElse(Future.successful(None))
            } { maybeQuestion =>
              onSuccess(
                tagService.find(
                  start = start.getOrElse(0),
                  end = end,
                  onlyDisplayed = true,
                  tagFilter = TagFilter(questionId = maybeQuestion.map(_.questionId))
                )
              ) { tags =>
                complete(tags)
              }
            }
          }
        }
      }
    }

  }
}

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
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.operation.OperationId
import org.make.core.reference.ThemeId
import org.make.core.tag.TagId
import org.make.core.{HttpCodes, Validation, tag}

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

  @ApiOperation(value = "list-tags", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "start", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "end", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "operationId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "themeId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[tag.Tag]])))
  @Path(value = "/")
  def listTags: Route = {
    get {
      path("tags") {
        makeOperation("Search") { _ =>
          parameters(
            (
              'start.as[Int].?,
              'end.as[Int].?,
              'operationId.?,
              'themeId.?,
              'country.?,
              'language.?
            )
          ) {
            (start,
             end,
             maybeOperationId,
             maybeThemeId,
             maybeCountry,
             maybeLanguage
            ) =>
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
                tagService.find(
                  start = start.getOrElse(0),
                  end = end,
                  tagFilter = TagFilter(
                    operationId = maybeOperationId.map(OperationId(_)),
                    themeId = maybeThemeId.map(ThemeId(_)),
                    country = maybeCountry,
                    language = maybeLanguage
                  )
                )
              ) { tags =>
                complete(tags)
              }
          }
        }
      }
    }
  }

  val tagRoutes: Route = getTag ~ listTags

  val tagId: PathMatcher1[TagId] =
    Segment.flatMap(id => Try(TagId(id)).toOption)
}

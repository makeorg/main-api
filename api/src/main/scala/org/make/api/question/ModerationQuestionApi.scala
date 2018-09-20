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

package org.make.api.question

import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, TotalCountHeader}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.{HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.concurrent.ExecutionContext.Implicits.global

@Api(value = "Moderation questions")
@Path(value = "/moderation/questions")
trait ModerationQuestionApi extends MakeAuthenticationDirectives with StrictLogging with ParameterExtractors {

  this: QuestionServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  @ApiOperation(
    value = "list-questions",
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
      new ApiImplicitParam(name = "operationId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "themeId", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Seq[QuestionResponse]]))
  )
  @Path(value = "/")
  def listQuestions: Route = get {
    path("moderation" / "questions") {
      makeOperation("ModerationSearchQuestion") { _ =>
        parameters(
          (
            'operationId.as[OperationId].?,
            'themeId.as[ThemeId].?,
            'country.as[Country].?,
            'language.as[Language].?,
            '_start.as[Int].?,
            '_end.as[Int].?,
            '_sort.?,
            '_order.?
          )
        ) { (operationId, themeId, country, language, start, end, sort, order) =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {

              val first = start.getOrElse(0)
              val request = SearchQuestionRequest(
                themeId,
                operationId,
                country,
                language,
                start,
                end.map(offset => offset - first),
                sort,
                order
              )
              val searchResults =
                questionService.countQuestion(request).flatMap { count =>
                  questionService.searchQuestion(request).map(results => count -> results)
                }

              onSuccess(searchResults) {
                case (count, results) =>
                  complete(
                    (
                      StatusCodes.OK,
                      scala.collection.immutable.Seq(TotalCountHeader(count.toString)),
                      results.map(QuestionResponse.apply)
                    )
                  )
              }
            }
          }
        }
      }
    }
  }

  val moderationQuestionRoutes: Route = listQuestions

}

object ModerationQuestionApi {}

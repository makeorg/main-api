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

package org.make.api.operation

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import com.typesafe.scalalogging.StrictLogging
import eu.timepit.refined.types.numeric.NonNegInt
import io.circe.generic.semiauto._
import io.circe.refined._
import eu.timepit.refined.auto._
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core._
import org.make.core.auth.UserRights
import org.make.core.question.QuestionId
import scalaoauth2.provider.AuthInfo

@Api(value = "Admin Question")
@Path(value = "/admin/questions")
trait AdminOperationOfQuestionApi extends Directives {

  @ApiOperation(
    value = "update-question-highlights",
    httpMethod = "PUT",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "questionId", paramType = "path", required = true, dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        required = true,
        dataType = "org.make.api.operation.UpdateHighlights"
      )
    )
  )
  @Path(value = "/{questionId}/highlights")
  def updateHighlights: Route

  def routes: Route = updateHighlights
}

trait AdminOperationOfQuestionApiComponent {
  def adminOperationOfQuestionApi: AdminOperationOfQuestionApi
}

trait DefaultAdminOperationOfQuestionApiComponent
    extends AdminOperationOfQuestionApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with OperationOfQuestionServiceComponent =>

  val questionId: PathMatcher1[QuestionId] = Segment.map(id => QuestionId(id))

  override lazy val adminOperationOfQuestionApi: DefaultAdminOperationOfQuestionApi =
    new DefaultAdminOperationOfQuestionApi

  class DefaultAdminOperationOfQuestionApi extends AdminOperationOfQuestionApi {
    override def updateHighlights: Route = put {
      path("admin" / "questions" / questionId / "highlights") { questionId =>
        makeOperation("UpdateQuestionHighlights") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[UpdateHighlights]) { request =>
                  provideAsyncOrNotFound(operationOfQuestionService.findByQuestionId(questionId)) {
                    operationOfQuestion =>
                      onSuccess(
                        operationOfQuestionService.update(
                          operationOfQuestion
                            .copy(
                              proposalsCount = request.proposalsCount,
                              participantsCount = request.participantsCount,
                              votesCount = request.votesCount
                            )
                        )
                      ) { _ =>
                        complete(StatusCodes.NoContent)
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

@ApiModel
final case class UpdateHighlights(proposalsCount: NonNegInt, participantsCount: NonNegInt, votesCount: NonNegInt)

object UpdateHighlights extends CirceFormatters {
  implicit val decoder: Decoder[UpdateHighlights] = deriveDecoder[UpdateHighlights]
  implicit val encoder: Encoder[UpdateHighlights] = deriveEncoder[UpdateHighlights]
}

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

package org.make.api.technical.generator.fixtures

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical._
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.HttpCodes
import org.make.core.operation.OperationId
import org.make.core.question.QuestionId

import scala.annotation.meta.field

@Api(value = "Fixtures")
@Path(value = "/fixtures")
trait FixturesApi extends Directives {
  @ApiOperation(value = "generate-fixtures", httpMethod = "POST", code = HttpCodes.Created)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        name = "body",
        paramType = "body",
        dataType = "org.make.api.technical.generator.fixtures.GenerateFixturesRequest"
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.Created, message = "Ok", response = classOf[FixtureResponse]))
  )
  @Path(value = "/generate")
  def generateFixtures: Route

  def routes: Route = generateFixtures
}

trait FixturesApiComponent {
  def fixturesApi: FixturesApi
}

trait DefaultFixturesApiComponent extends FixturesApiComponent with MakeAuthenticationDirectives with StrictLogging {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SessionHistoryCoordinatorServiceComponent
    with FixturesServiceComponent =>

  override lazy val fixturesApi: FixturesApi = new DefaultFixturesApi

  class DefaultFixturesApi extends FixturesApi {
    override def generateFixtures: Route = post {
      path("fixtures" / "generate") {
        makeOperation("GenerateFixtures") { requestContext =>
          withoutRequestTimeout {
            decodeRequest {
              entity(as[GenerateFixturesRequest]) { request =>
                provideAsync(
                  fixturesService.generate(maybeOperationId = request.operationId, maybeQuestionId = request.questionId)
                ) { result =>
                  complete(StatusCodes.Created -> result)
                }
              }
            }
          }
        }
      }
    }
  }

}

final case class GenerateFixturesRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "b924bb35-9e49-43c5-bf63-da4f56b13a5e")
  operationId: Option[OperationId],
  @(ApiModelProperty @field)(dataType = "string", example = "ad27fa8e-9cd4-4986-b1b4-7969c064322f")
  questionId: Option[QuestionId]
)

object GenerateFixturesRequest {
  implicit val decoder: Decoder[GenerateFixturesRequest] = deriveDecoder[GenerateFixturesRequest]
}

final case class FixtureResponse(
  @(ApiModelProperty @field)(dataType = "string", example = "92f8877d-a514-4b0e-adbe-b9906c431156")
  operationId: OperationId,
  @(ApiModelProperty @field)(dataType = "string", example = "1f1d9f32-de98-47ea-9e44-79e95e91b6cc")
  questionId: QuestionId,
  userCount: Int,
  organisationCount: Int,
  partnerCount: Int,
  tagCount: Int,
  proposalCount: Int,
  organisationsVoteCount: Int
)

object FixtureResponse {
  implicit val encoder: Encoder[FixtureResponse] = deriveEncoder[FixtureResponse]
}

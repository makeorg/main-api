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

import java.time.LocalDate

import javax.ws.rs.Path
import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.{HttpCodes, ParameterExtractors}
import org.make.core.operation._
import org.make.core.reference.Country

@Api(value = "Operation")
@Path(value = "/operations")
trait OperationApi extends Directives {

  @ApiOperation(value = "get-operations", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Array[OperationResponse]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "slug", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", required = false, dataType = "string"),
      new ApiImplicitParam(name = "openAt", paramType = "query", required = false, dataType = "date")
    )
  )
  @Path(value = "/")
  def getOperations: Route

  @ApiOperation(value = "get-operation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OperationResponse]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "operationId", paramType = "path", dataType = "string")))
  @Path(value = "/{operationId}")
  def getOperation: Route

  def routes: Route = getOperations ~ getOperation

}

trait OperationApiComponent {
  def operationApi: OperationApi
}

trait DefaultOperationApiComponent
    extends OperationApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: OperationServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeSettingsComponent
    with OperationServiceComponent
    with TagServiceComponent =>

  override lazy val operationApi: OperationApi = new DefaultOperationApi

  class DefaultOperationApi extends OperationApi {

    val operationId: PathMatcher1[OperationId] = Segment.map(id => OperationId(id))

    override def getOperations: Route = {
      get {
        path("operations") {
          parameters(('slug.?, 'country.as[Country].?, 'openAt.as[LocalDate].?)) { (slug, country, openAt) =>
            makeOperation("GetOperations") { requestContext =>
              provideAsync(
                operationService
                  .find(slug = slug, country = country, maybeSource = requestContext.source, openAt = openAt)
              ) { result =>
                provideAsync(tagService.findByQuestionIds(result.flatMap(_.questions).map(_.question.questionId))) {
                  tags =>
                    complete(result.map(operation => OperationResponse(operation, tags)))
                }
              }
            }
          }
        }
      }
    }

    override def getOperation: Route = {
      get {
        path("operations" / operationId) { operationId =>
          makeOperation("GetOperation") { _ =>
            provideAsyncOrNotFound(operationService.findOne(operationId)) { operation =>
              provideAsync(tagService.findByQuestionIds(operation.questions.map(_.question.questionId))) { tags =>
                complete(OperationResponse(operation, tags))
              }
            }
          }
        }
      }
    }
  }
}

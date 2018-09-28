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

package org.make.api.widget

import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller.CsvSeq
import io.circe.ObjectEncoder
import io.circe.generic.semiauto.deriveEncoder
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal._
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.auth.UserRights
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.IndexedProposal
import org.make.core.tag.TagId
import org.make.core.{HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.collection.immutable

@Api(value = "Widget")
@Path(value = "/widget")
trait WidgetApi extends MakeAuthenticationDirectives with ParameterExtractors {
  this: MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent with WidgetServiceComponent =>

  @Path(value = "/operations/{operationId}/start-sequence")
  @ApiOperation(value = "get-widget-sequence", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultSeededResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "operationId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "tagsIds", paramType = "query", dataType = "string", allowMultiple = true),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer")
    )
  )
  def getWidgetSequence: Route = {
    get {
      path("widget" / "operations" / widgetOperationId / "start-sequence") { widgetOperationId =>
        makeOperation("GetWidgetSequence") { requestContext =>
          optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
            parameters(('tagsIds.as[immutable.Seq[TagId]].?, 'limit.as[Int].?)) {
              (tagsIds: Option[Seq[TagId]], limit: Option[Int]) =>
                provideAsync(
                  widgetService.startNewWidgetSequence(
                    maybeUserId = userAuth.map(_.user.userId),
                    widgetOperationId = widgetOperationId,
                    tagsIds = tagsIds,
                    limit = limit,
                    requestContext = requestContext
                  )
                ) { proposalsResultSeededResponse: ProposalsResultSeededResponse =>
                  complete(proposalsResultSeededResponse)
                }
            }
          }
        }
      }
    }
  }

  val widgetRoutes: Route = getWidgetSequence

  private val widgetOperationId: PathMatcher1[OperationId] = Segment.map(id => OperationId(id))
}

final case class WidgetSequence(title: String, slug: String, proposals: Seq[IndexedProposal])

object WidgetSequence {
  implicit val encoder: ObjectEncoder[WidgetSequence] = deriveEncoder[WidgetSequence]
}

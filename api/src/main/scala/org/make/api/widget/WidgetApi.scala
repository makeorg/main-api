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
import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.operation.OperationId
import org.make.core.proposal.indexed.{IndexedProposal, ProposalsSearchResult}
import org.make.core.proposal.{OperationSearchFilter, SearchFilters, SearchQuery, TagsSearchFilter}
import org.make.core.tag.TagId

import scala.collection.immutable

@Api(value = "Widget")
@Path(value = "/widget")
trait WidgetApi extends MakeAuthenticationDirectives {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ProposalSearchEngineComponent =>

  @Path(value = "/operations/{operationId}/sequence")
  @ApiOperation(value = "get-widget-sequence", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[WidgetSequence])))
  @ApiImplicitParams(value = Array(
    new ApiImplicitParam(name = "operationId", paramType = "path", dataType = "string"),
    new ApiImplicitParam(name = "tagsIds", paramType = "query", dataType = "string", allowMultiple = true)
  ))
  def getWidgetSequence: Route = {
    get {
      path("widget" / "operations" / widgetOperationId / "sequence") { widgetOperationId =>
        makeOperation("GetWidgetSequence") { _ =>
          parameters('tagsIds.as(CsvSeq[String]).?) { tagsIdsParams =>
            val tagsIds: Option[immutable.Seq[TagId]] = tagsIdsParams.map(_.map(TagId.apply))
            provideAsync(
              elasticsearchProposalAPI.searchProposals(
                SearchQuery(
                  filters = Some(SearchFilters(
                    tags = tagsIds.map(TagsSearchFilter(_)),
                    operation = Some(OperationSearchFilter(widgetOperationId))
                  ))
                )
              )
            ) { proposalsSearchResult: ProposalsSearchResult =>
              complete(proposalsSearchResult)
            }
          }
        }
      }
    }
  }

  val widgetRoutes: Route = getWidgetSequence

  private val widgetOperationId: PathMatcher1[OperationId] = Segment.map(id => OperationId(id))
}

final case class WidgetSequence(title: String,
                                slug: String,
                                proposals: Seq[IndexedProposal])

object WidgetSequence {
  implicit val encoder: ObjectEncoder[WidgetSequence] = deriveEncoder[WidgetSequence]
}

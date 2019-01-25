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

package org.make.api.sequence

import akka.http.scaladsl.server._
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import org.make.core.proposal.ProposalId
import org.make.core.sequence._
import scalaoauth2.provider.AuthInfo

@Api(value = "Sequence")
@Path(value = "/sequences")
trait SequenceApi extends Directives {

  @ApiOperation(value = "start-sequence-by-id", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceResult])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "id", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "include", paramType = "query", dataType = "string", allowMultiple = true)
    )
  )
  @Path(value = "/start/{id}")
  def startSequenceById: Route

  def routes: Route = startSequenceById
}

trait SequenceApiComponent {
  def sequenceApi: SequenceApi
}

trait DefaultSequenceApiComponent extends SequenceApiComponent with MakeAuthenticationDirectives with StrictLogging {
  this: SequenceServiceComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  override lazy val sequenceApi: SequenceApi = new SequenceApi {

    val sequenceId: PathMatcher1[SequenceId] = Segment.map(id => SequenceId(id))

    def startSequenceById: Route = {
      get {
        path("sequences" / "start" / sequenceId) { sequenceId =>
          parameters('include.*) { includes =>
            makeOperation("StartSequenceById") { requestContext =>
              optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
                decodeRequest {
                  provideAsyncOrNotFound(
                    sequenceService
                      .startNewSequence(
                        maybeUserId = userAuth.map(_.user.userId),
                        sequenceId = sequenceId,
                        includedProposals = includes.toSeq.map(ProposalId(_)),
                        tagsIds = None,
                        requestContext = requestContext
                      )
                  ) { sequences =>
                    complete(sequences)
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

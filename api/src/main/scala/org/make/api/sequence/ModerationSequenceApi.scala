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
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceId
import scalaoauth2.provider.AuthInfo

@Api(value = "Moderation Sequence")
@Path(value = "/moderation/sequences")
trait ModerationSequenceApi extends Directives {

  @ApiOperation(
    value = "moderation-get-sequence-config",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceConfiguration]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "sequenceId", paramType = "path", dataType = "string")))
  @Path(value = "/{sequenceId}/configuration")
  def getModerationSequenceConfiguration: Route

  @ApiOperation(
    value = "moderation-update-sequence-configuration",
    httpMethod = "PUT",
    code = HttpCodes.OK,
    notes = "/!\\ You need to reindex proposals to apply these modifications.",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Boolean])))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.sequence.SequenceConfigurationRequest"
      ),
      new ApiImplicitParam(name = "sequenceId", paramType = "path", required = true, value = "", dataType = "string"),
      new ApiImplicitParam(name = "questionId", paramType = "path", required = true, value = "", dataType = "string")
    )
  )
  @Path(value = "/{sequenceId}/{questionId}/configuration")
  def putSequenceConfiguration: Route

  def routes: Route = getModerationSequenceConfiguration ~ putSequenceConfiguration

}

trait ModerationSequenceApiComponent {
  def moderationSequenceApi: ModerationSequenceApi
}

trait DefaultModerationSequenceApiComponent
    extends ModerationSequenceApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging {

  this: SequenceServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with SequenceConfigurationComponent =>

  override lazy val moderationSequenceApi: ModerationSequenceApi = new ModerationSequenceApi {

    val moderationSequenceId: PathMatcher1[SequenceId] = Segment.map(id => SequenceId(id))
    private val questionId: PathMatcher1[QuestionId] = Segment.map(id   => QuestionId(id))

    override def getModerationSequenceConfiguration: Route = {
      get {
        path("moderation" / "sequences" / moderationSequenceId / "configuration") { sequenceId =>
          makeOperation("GetModerationSequenceConfiguration") { _ =>
            makeOAuth2 { auth: AuthInfo[UserRights] =>
              requireAdminRole(auth.user) {
                provideAsyncOrNotFound[SequenceConfiguration](
                  sequenceConfigurationService.getPersistentSequenceConfiguration(sequenceId)
                ) { complete(_) }
              }
            }
          }
        }
      }
    }
    override def putSequenceConfiguration: Route = {
      put {
        path("moderation" / "sequences" / moderationSequenceId / questionId / "configuration") {
          (sequenceId, questionId) =>
            makeOperation("PostSequenceConfiguration") { _ =>
              makeOAuth2 { auth: AuthInfo[UserRights] =>
                requireAdminRole(auth.user) {
                  decodeRequest {
                    entity(as[SequenceConfigurationRequest]) {
                      sequenceConfigurationRequest: SequenceConfigurationRequest =>
                        provideAsync[Boolean](
                          sequenceConfigurationService
                            .setSequenceConfiguration(
                              sequenceConfigurationRequest.toSequenceConfiguration(sequenceId, questionId)
                            )
                        ) { complete(_) }
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
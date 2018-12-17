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
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.operation.OperationServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, ReadJournalComponent}
import org.make.api.theme.ThemeServiceComponent
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import org.make.core.proposal.ProposalId
import org.make.core.question.QuestionId
import org.make.core.sequence._
import org.make.core.sequence.indexed.IndexedStartSequence
import scalaoauth2.provider.AuthInfo

@Api(value = "Sequence")
@Path(value = "/")
trait SequenceApi extends MakeAuthenticationDirectives with StrictLogging {
  this: SequenceServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ThemeServiceComponent
    with OperationServiceComponent
    with SequenceConfigurationComponent
    with ReadJournalComponent
    with ActorSystemComponent =>

  @ApiOperation(
    value = "moderation-get-sequence-config",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[SequenceConfiguration]))
  )
  @ApiImplicitParams(value = Array(new ApiImplicitParam(name = "sequenceId", paramType = "path", dataType = "string")))
  @Path(value = "/moderation/sequences/{sequenceId}/configuration")
  def getModerationSequenceConfiguration: Route = {
    get {
      path("moderation" / "sequences" / sequenceId / "configuration") { sequenceId =>
        makeOperation("GetModerationSequenceConfiguration") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              provideAsyncOrNotFound[SequenceConfiguration](
                sequenceConfigurationService.getPersistentSequenceConfiguration(sequenceId)
              ) { complete(_) }
            }
          }
        }
      }
    }
  }

  @ApiOperation(
    value = "moderation-update-sequence-configuration",
    httpMethod = "PUT",
    code = HttpCodes.OK,
    notes = "/!\\ You need to reindex proposals to apply these modifications.",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "user", description = "application user"),
          new AuthorizationScope(scope = "admin", description = "BO Admin")
        )
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
  @Path(value = "/moderation/sequences/{sequenceId}/{questionId}/configuration")
  def putSequenceConfiguration: Route =
    put {
      path("moderation" / "sequences" / sequenceId / questionId / "configuration") { (sequenceId, questionId) =>
        makeOperation("PostSequenceConfiguration") { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireModerationRole(auth.user) {
              decodeRequest {
                entity(as[SequenceConfigurationRequest]) { sequenceConfigurationRequest: SequenceConfigurationRequest =>
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

  @ApiOperation(value = "start-sequence-by-id", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Option[IndexedStartSequence]]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "id", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "include", paramType = "query", dataType = "string", allowMultiple = true)
    )
  )
  @Path(value = "/sequences/start/{id}")
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

  val sequenceRoutes: Route =
    startSequenceById ~
      getModerationSequenceConfiguration ~
      putSequenceConfiguration

  val sequenceId: PathMatcher1[SequenceId] = Segment.map(id         => SequenceId(id))
  private val questionId: PathMatcher1[QuestionId] = Segment.map(id => QuestionId(id))
  val sequenceSlug: PathMatcher1[String] = Segment
}

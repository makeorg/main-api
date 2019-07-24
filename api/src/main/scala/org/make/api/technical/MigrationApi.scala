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

package org.make.api.technical

import java.time.ZonedDateTime

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import io.swagger.annotations.{Authorization, _}
import javax.ws.rs.Path
import org.make.api.ActorSystemComponent
import org.make.api.extensions.{MailJetConfigurationComponent, MakeSettingsComponent}
import org.make.api.proposal.{ProposalCoordinatorServiceComponent, UpdateProposalVotesVerifiedCommand}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.crm.CrmServiceComponent
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.{ProposalId, Vote}
import org.make.core.tag.{Tag => _}
import org.make.core.{CirceFormatters, DateHelper, HttpCodes, Validation}
import io.circe.generic.semiauto.deriveDecoder

import scala.annotation.meta.field
import scala.concurrent.Future

@Api(value = "Migrations")
@Path(value = "/migrations")
trait MigrationApi extends Directives {

  def emptyRoute: Route

  @ApiOperation(
    value = "reset-qualification-count",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok")))
  @Path(value = "/reset-qualification-count")
  def resetQualificationCount: Route

  @ApiOperation(
    value = "delete-mailjet-anoned-contacts",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.Accepted, message = "Accepted")))
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.technical.DeleteContactsRequest"
      )
    )
  )
  @Path(value = "/delete-mailjet-anoned-contacts")
  def deleteMailjetAnonedContacts: Route

  def routes: Route = resetQualificationCount ~ deleteMailjetAnonedContacts
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with StrictLogging {
  this: MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent
    with ProposalCoordinatorServiceComponent
    with ActorSystemComponent
    with ReadJournalComponent
    with SessionHistoryCoordinatorServiceComponent
    with CrmServiceComponent
    with MailJetConfigurationComponent =>

  override lazy val migrationApi: MigrationApi = new DefaultMigrationApi

  class DefaultMigrationApi extends MigrationApi {
    override def emptyRoute: Route =
      get {
        path("migrations") {
          complete(StatusCodes.OK)
        }
      }

    override def resetQualificationCount: Route = post {
      path("migrations" / "reset-qualification-count") {
        withoutRequestTimeout {
          makeOperation("ResetQualificationCount") { requestContext =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)
                val futureToComplete: Future[Done] = proposalJournal
                  .currentPersistenceIds()
                  .mapAsync(4) { id =>
                    val proposalId = ProposalId(id)
                    proposalCoordinatorService.getProposal(proposalId)
                  }
                  .filter(_.exists(_.status == Accepted))
                  .mapAsync(4) { maybeProposal =>
                    val proposal = maybeProposal.get
                    def updateCommand(votes: Seq[Vote]): UpdateProposalVotesVerifiedCommand = {
                      val votesResetQualifVerified = votes.map { vote =>
                        vote.copy(
                          countVerified = vote.count,
                          qualifications = vote.qualifications.map(q => q.copy(countVerified = q.count))
                        )
                      }
                      UpdateProposalVotesVerifiedCommand(
                        moderator = userAuth.user.userId,
                        proposalId = proposal.proposalId,
                        requestContext = requestContext,
                        updatedAt = DateHelper.now(),
                        votesVerified = votesResetQualifVerified
                      )
                    }
                    proposalCoordinatorService.updateVotesVerified(updateCommand(proposal.votes))
                  }
                  .runForeach(_ => Done)
                provideAsync(futureToComplete) { _ =>
                  complete(StatusCodes.NoContent)
                }
              }
            }
          }
        }
      }
    }

    override def deleteMailjetAnonedContacts: Route = post {
      path("migrations" / "delete-mailjet-anoned-contacts") {
        withoutRequestTimeout {
          makeOperation("DeleteMailjetAnonedContacts") { _ =>
            makeOAuth2 { userAuth =>
              requireAdminRole(userAuth.user) {
                decodeRequest {
                  entity(as[DeleteContactsRequest]) { req =>
                    crmService.deleteAllContactsBefore(req.maxUpdatedAtBeforeDelete, req.deleteEmptyProperties)
                    complete(StatusCodes.Accepted)
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

final case class DeleteContactsRequest(@(ApiModelProperty @field)(
                                         dataType = "string",
                                         example = "2019-07-11T11:21:40.508Z"
                                       ) maxUpdatedAtBeforeDelete: ZonedDateTime,
                                       @(ApiModelProperty @field)(dataType = "boolean") deleteEmptyProperties: Boolean) {
  Validation.validate(
    Validation.validateField(
      "maxUpdatedAtBeforeDelete",
      "invalid_date",
      maxUpdatedAtBeforeDelete.isBefore(ZonedDateTime.now.minusDays(1)),
      "DeleteFor cannot be set to a date more recent than yesterday."
    )
  )
}

object DeleteContactsRequest extends CirceFormatters {
  implicit val decoder: Decoder[DeleteContactsRequest] = deriveDecoder[DeleteContactsRequest]

}

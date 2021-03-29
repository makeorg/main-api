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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.persistence.cassandra.reconciler.{Reconciliation, ReconciliationSettings}
import com.typesafe.config.ConfigValueFactory
import grizzled.slf4j.Logging
import io.swagger.annotations.{Authorization, _}
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MailJetConfigurationComponent
import org.make.api.operation.{OperationServiceComponent, PersistentOperationOfQuestionServiceComponent}
import org.make.api.proposal.ProposalCoordinatorComponent
import org.make.api.question.QuestionServiceComponent
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.job.JobCoordinatorComponent
import org.make.api.technical.storage.StorageConfigurationComponent
import org.make.api.user.UserServiceComponent
import org.make.core._
import org.make.core.tag.{Tag => _}

import javax.ws.rs.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

@Api(value = "Migrations")
@Path(value = "/migrations")
trait MigrationApi extends Directives {

  def emptyRoute: Route

  @ApiOperation(
    value = "migrate-persistence-ids",
    httpMethod = "POST",
    code = HttpCodes.NoContent,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @Path(value = "/migrate-persistence-ids")
  def migratePersistenceIds: Route

  def routes: Route = migratePersistenceIds
}

trait MigrationApiComponent {
  def migrationApi: MigrationApi
}

trait DefaultMigrationApiComponent extends MigrationApiComponent with MakeAuthenticationDirectives with Logging {
  this: MakeDirectivesDependencies
    with ActorSystemComponent
    with MailJetConfigurationComponent
    with UserServiceComponent
    with ReadJournalComponent
    with JobCoordinatorComponent
    with OperationServiceComponent
    with ProposalCoordinatorComponent
    with QuestionServiceComponent
    with EventBusServiceComponent
    with StorageConfigurationComponent
    with PersistentOperationOfQuestionServiceComponent =>

  override lazy val migrationApi: MigrationApi = new DefaultMigrationApi

  class DefaultMigrationApi extends MigrationApi {
    override def emptyRoute: Route =
      get {
        path("migrations") {
          complete(StatusCodes.OK)
        }
      }

    override def migratePersistenceIds: Route = post {
      path("migrations" / "migrate-persistence-ids") {
        makeOperation("MigratePersistenceIds") { requestContext =>
          makeOAuth2 { userAuth =>
            requireAdminRole(userAuth.user) {
              val rec = new Reconciliation(
                actorSystem,
                new ReconciliationSettings(
                  actorSystem.settings.config
                    .getConfig("akka.persistence.cassandra.reconciler")
                    .withValue("plugin-location", ConfigValueFactory.fromAnyRef(s"make-api.event-sourcing.sessions"))
                )
              )
              rec.rebuildAllPersistenceIds().onComplete {
                case Success(_)         => logger.info(s"sessions ids table populated")
                case Failure(exception) => logger.error(s"error while populating sessions ids table", exception)
              }
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }
  }
}

package org.make.api.technical

import javax.ws.rs.Path

import akka.Done
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.persistence.cassandra.EventsByTagMigration
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import org.make.api.ActorSystemComponent
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.core.HttpCodes
import org.make.core.auth.UserRights

import scala.concurrent.{ExecutionException, Future}
import scalaoauth2.provider.AuthInfo
import scala.concurrent.ExecutionContext.Implicits.global

@Path("/migrations")
@Api(value = "Migrations")
trait MigrationApi extends MakeAuthenticationDirectives with StrictLogging {
  self: ActorSystemComponent with MakeDataHandlerComponent with IdGeneratorComponent with MakeSettingsComponent =>

  @ApiOperation(
    value = "migrate-cassandra",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content", response = classOf[Unit]))
  )
  @Path("/cassandra")
  def migrateCassandra: Route = post {
    path("migrations" / "cassandra") {
      makeTrace("MigrateCassandra") { _ =>
        val migrator = EventsByTagMigration(actorSystem)

        val schemaMigration: Future[Done] = for {
          _ <- migrator.createTables()
          done <- migrator.addTagsColumn().recover {
            case i: ExecutionException if i.getMessage.contains("conflicts with an existing column") => Done
          }
        } yield done

        onComplete(schemaMigration) { _ =>
          complete(StatusCodes.NoContent)
        }
      }
    }
  }

  val migrationRoutes: Route = migrateCassandra
}

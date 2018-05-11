package org.make.api.organisation

import akka.http.scaladsl.server.{PathMatcher1, Route}
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeAuthentication
import org.make.api.technical.{IdGeneratorComponent, MakeDirectives}
import org.make.api.user.{OrganisationServiceComponent, UserResponse}
import org.make.core.HttpCodes
import org.make.core.user.UserId

import scala.util.Try

@Api(value = "Organisations")
@Path(value = "/organisations")
trait OrganisationApi extends MakeDirectives with StrictLogging {
  this: OrganisationServiceComponent with IdGeneratorComponent with MakeSettingsComponent with MakeAuthentication =>

  @ApiOperation(value = "get-organisation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  @Path(value = "/{organisationId}")
  def getOrganisation: Route =
    get {
      path("organisations" / organisationId) { organisationId =>
        makeOperation("GetOrganisation") { _ =>
          provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { user =>
            complete(UserResponse(user))
          }
        }
      }
    }

  val organisationRoutes: Route = getOrganisation

  val organisationId: PathMatcher1[UserId] = Segment.flatMap(id => Try(UserId(id)).toOption)

}

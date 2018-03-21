package org.make.api.technical.healthcheck
import akka.http.scaladsl.server.Route
import io.swagger.annotations.{Api, _}
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import scalaoauth2.provider.AuthInfo

@Api(value = "Health Check")
@Path(value = "/")
trait HealthCheckApi extends MakeAuthenticationDirectives {
  this: MakeSettingsComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with HealthCheckServiceComponent =>

  @ApiOperation(
    value = "healthcheck",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[Map[String, String]]))
  )
  @Path(value = "/healthcheck")
  def healthCheck: Route = get {
    path("healthcheck") {
      makeOAuth2 { auth: AuthInfo[UserRights] =>
        requireAdminRole(auth.user) {
          makeOperation("HealthCheck") { _ =>
            provideAsync(healthCheckService.runAllHealthChecks())(result => complete(result))
          }
        }
      }
    }
  }

  val healthCheckRoutes: Route = healthCheck
}

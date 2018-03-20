package org.make.api.technical.businessconfig

import javax.ws.rs.Path

import akka.http.scaladsl.server.Route
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, MakeDirectives, ShortenedNames}
import org.make.api.theme.ThemeServiceComponent
import org.make.core.HttpCodes
import org.make.core.auth.UserRights

import scalaoauth2.provider.AuthInfo

@Api(value = "Configurations")
@Path(value = "/configurations")
trait ConfigurationsApi extends MakeDirectives with MakeAuthenticationDirectives with ShortenedNames {
  self: MakeDataHandlerComponent with IdGeneratorComponent with ThemeServiceComponent with MakeSettingsComponent =>

  @Path(value = "front")
  @ApiOperation(value = "front-configuration", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[FrontConfiguration]))
  )
  def businessConfigurationFront: Route =
    get {
      path("configurations" / "front") {
        makeOperation("FrontConfiguration") { _ =>
          onSuccess(themeService.findAll()) { themes =>
            complete(FrontConfiguration.default(themes = themes))
          }
        }
      }
    }

  @Path(value = "backoffice")
  @ApiOperation(
    value = "backoffice-configuration",
    httpMethod = "GET",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(
          new AuthorizationScope(scope = "admin", description = "BO Admin"),
          new AuthorizationScope(scope = "moderator", description = "BO Moderator")
        )
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[BackofficeConfiguration]))
  )
  def businessConfigBack: Route =
    get {
      path("configurations" / "backoffice") {
        makeOperation("BackofficeConfiguration") { _ =>
          makeOAuth2 { userAuth: AuthInfo[UserRights] =>
            requireModerationRole(userAuth.user) {
              onSuccess(themeService.findAll()) { themes =>
                complete(BackofficeConfiguration.default(themes = themes))
              }
            }
          }
        }
      }
    }

  val businessConfigRoutes: Route = businessConfigurationFront ~ businessConfigBack

}

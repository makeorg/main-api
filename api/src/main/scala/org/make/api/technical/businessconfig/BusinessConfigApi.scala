package org.make.api.technical.businessconfig

import javax.ws.rs.Path

import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import io.swagger.annotations._
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives, MakeDirectives, ShortenedNames}
import org.make.api.theme.ThemeServiceComponent
import org.make.core.HttpCodes
import org.make.core.user.Role.{RoleAdmin, RoleModerator}
import org.make.core.user.User

import scalaoauth2.provider.AuthInfo

@Api(value = "Business Config")
@Path(value = "/")
trait BusinessConfigApi extends MakeDirectives with MakeAuthenticationDirectives with ShortenedNames {
  self: MakeDataHandlerComponent with IdGeneratorComponent with ThemeServiceComponent with MakeSettingsComponent =>

  @Path(value = "business_config_prod")
  @ApiOperation(value = "business_config_prod", httpMethod = "GET", code = HttpCodes.OK)
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[BusinessConfigFront]))
  )
  def businessConfigProd: Route =
    get {
      path("business_config_prod") {
        makeTrace("BusinessConfigFront") { _ =>
          onSuccess(themeService.findAll()) { themes =>
            complete(BusinessConfigFront.default(themes = themes))
          }
        }
      }
    }

  @Path(value = "business_config_back")
  @ApiOperation(
    value = "business_config_back",
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
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[BusinessConfigBack]))
  )
  def businessConfigBack: Route =
    get {
      path("business_config_back") {
        makeTrace("BusinessConfigBack") { _ =>
          makeOAuth2 { userAuth: AuthInfo[User] =>
            authorize(userAuth.user.roles.exists(role => role == RoleAdmin || role == RoleModerator)) {
              onSuccess(themeService.findAll()) { themes =>
                complete(BusinessConfigBack.default(themes = themes))
              }
            }
          }
        }
      }
    }

  val businessConfigRoutes: Route = businessConfigProd ~ businessConfigBack

}

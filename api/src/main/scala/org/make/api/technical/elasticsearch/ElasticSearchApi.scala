package org.make.api.technical.elasticsearch

import javax.ws.rs.Path

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.swagger.annotations.{Api, _}
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.auth.UserRights

import scalaoauth2.provider.AuthInfo

@Api(value = "Elasticsearch")
@Path(value = "/")
trait ElasticSearchApi extends MakeAuthenticationDirectives {
  this: DefaultIndexationComponent with MakeSettingsComponent with MakeDataHandlerComponent with IdGeneratorComponent =>

  @ApiOperation(
    value = "reindex",
    httpMethod = "POST",
    code = HttpCodes.OK,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[String])))
  @Path(value = "/technical/elasticsearch/reindex")
  def reindex: Route = post {
    path("technical" / "elasticsearch" / "reindex") {
      makeOAuth2 { auth: AuthInfo[UserRights] =>
        requireAdminRole(auth.user) {
          makeTrace("ReindexingData") { _ =>
            provideAsync(indexationService.reindexData()) { result =>
              complete(StatusCodes.NoContent)
            }
          }
        }
      }
    }
  }

  val elasticsearchRoutes: Route = reindex
}

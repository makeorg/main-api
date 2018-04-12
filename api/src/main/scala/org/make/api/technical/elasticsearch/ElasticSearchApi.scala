package org.make.api.technical.elasticsearch

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.{Api, _}
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

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
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(value = "body", paramType = "body", dataType = "org.make.api.technical.elasticsearch.ReindexRequest")
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[String])))
  @Path(value = "/technical/elasticsearch/reindex")
  def reindex: Route = post {
    path("technical" / "elasticsearch" / "reindex") {
      makeOAuth2 { auth: AuthInfo[UserRights] =>
        requireAdminRole(auth.user) {
          decodeRequest {
            entity(as[ReindexRequest]) { request: ReindexRequest =>
              makeOperation("ReindexingData") { _ =>
                provideAsync(indexationService.reindexData(request.force)) { result =>
                  complete(StatusCodes.NoContent)
                }
              }
            }
          }
        }
      }
    }
  }

  val elasticsearchRoutes: Route = reindex
}

@ApiModel
final case class ReindexRequest(@(ApiModelProperty @field)(example = "true") force: Boolean)

object ReindexRequest {
  implicit val decoder: Decoder[ReindexRequest] = deriveDecoder[ReindexRequest]
}
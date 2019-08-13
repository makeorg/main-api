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

package org.make.api.technical.elasticsearch

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.{Api, _}
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(value = "Elasticsearch")
@Path(value = "/")
trait ElasticSearchApi extends Directives {

  @ApiOperation(
    value = "reindex",
    httpMethod = "POST",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.technical.elasticsearch.ReindexRequest"
      )
    )
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.NoContent, message = "No Content")))
  @Path(value = "/technical/elasticsearch/reindex")
  def reindex: Route

  final def routes: Route = reindex
}

trait ElasticSearchApiComponent {
  def elasticSearchApi: ElasticSearchApi
}

trait DefaultElasticSearchApiComponent extends ElasticSearchApiComponent with MakeAuthenticationDirectives {
  this: DefaultIndexationComponent
    with MakeSettingsComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent =>

  override lazy val elasticSearchApi: ElasticSearchApi = new DefaultElasticSearchApi

  class DefaultElasticSearchApi extends ElasticSearchApi {
    def reindex: Route = post {
      path("technical" / "elasticsearch" / "reindex") {
        makeOAuth2 { auth: AuthInfo[UserRights] =>
          requireAdminRole(auth.user) {
            decodeRequest {
              entity(as[ReindexRequest]) { request: ReindexRequest =>
                makeOperation("ReindexingData") { _ =>
                  provideAsync(
                    indexationService.reindexData(
                      Seq(request.forceAll, request.forceIdeas).flatten.contains(true),
                      Seq(request.forceAll, request.forceOrganisations).flatten.contains(true),
                      Seq(request.forceAll, request.forceProposals).flatten.contains(true),
                      Seq(request.forceAll, request.forceOperationOfQuestions).flatten.contains(true)
                    )
                  ) { _ =>
                    complete(StatusCodes.NoContent)
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

@ApiModel
final case class ReindexRequest(
  @(ApiModelProperty @field)(example = "true", dataType = "boolean") forceIdeas: Option[Boolean],
  @(ApiModelProperty @field)(example = "true", dataType = "boolean") forceOrganisations: Option[Boolean],
  @(ApiModelProperty @field)(example = "true", dataType = "boolean") forceProposals: Option[Boolean],
  @(ApiModelProperty @field)(example = "true", dataType = "boolean") forceOperationOfQuestions: Option[Boolean],
  @(ApiModelProperty @field)(example = "true", dataType = "boolean") forceAll: Option[Boolean]
)

object ReindexRequest {
  implicit val decoder: Decoder[ReindexRequest] = deriveDecoder[ReindexRequest]
}

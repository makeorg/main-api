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
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical.{EndpointType, MakeAuthenticationDirectives}
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import org.make.core.job.Job.JobId.{Reindex, ReindexPosts}
import scalaoauth2.provider.AuthInfo

import scala.annotation.meta.field

@Api(value = "Elasticsearch")
@Path(value = "/technical/elasticsearch")
trait ElasticSearchApi extends Directives {

  @ApiOperation(
    value = "reindex",
    httpMethod = "POST",
    code = HttpCodes.Accepted,
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
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.Accepted, message = "Ok"),
      new ApiResponse(code = HttpCodes.Conflict, message = "Conflict")
    )
  )
  @Path(value = "/reindex")
  def reindex: Route

  @ApiOperation(
    value = "reindex-posts",
    httpMethod = "POST",
    code = HttpCodes.Accepted,
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "admin", description = "BO Admin"))
      )
    )
  )
  @ApiResponses(
    value = Array(
      new ApiResponse(code = HttpCodes.Accepted, message = "Accepted"),
      new ApiResponse(code = HttpCodes.Conflict, message = "Conflict")
    )
  )
  @Path(value = "/reindex-posts")
  def reindexPosts: Route

  final def routes: Route = reindex ~ reindexPosts
}

trait ElasticSearchApiComponent {
  def elasticSearchApi: ElasticSearchApi
}

trait DefaultElasticSearchApiComponent extends ElasticSearchApiComponent with MakeAuthenticationDirectives {
  this: MakeDirectivesDependencies with IndexationComponent =>

  override lazy val elasticSearchApi: ElasticSearchApi = new DefaultElasticSearchApi

  class DefaultElasticSearchApi extends ElasticSearchApi {
    def reindex: Route = post {
      path("technical" / "elasticsearch" / "reindex") {
        makeOperation(Reindex.value) { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              decodeRequest {
                entity(as[ReindexRequest]) { request: ReindexRequest =>
                  // Do not wait until the reindexation job is over to give an answer
                  provideAsync(
                    indexationService.reindexData(
                      Seq(request.forceAll, request.forceIdeas).flatten.contains(true),
                      Seq(request.forceAll, request.forceOrganisations).flatten.contains(true),
                      Seq(request.forceAll, request.forceProposals).flatten.contains(true),
                      Seq(request.forceAll, request.forceOperationOfQuestions).flatten.contains(true)
                    )
                  ) { acceptance =>
                    if (acceptance.isAccepted) {
                      complete(StatusCodes.Accepted)
                    } else {
                      complete(StatusCodes.Conflict)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    def reindexPosts: Route = post {
      path("technical" / "elasticsearch" / "reindex-posts") {
        makeOperation(ReindexPosts.value, EndpointType.CoreOnly) { _ =>
          makeOAuth2 { auth: AuthInfo[UserRights] =>
            requireAdminRole(auth.user) {
              // Do not wait until the reindexation job is over to give an answer
              provideAsync(indexationService.reindexPostsData()) { acceptance =>
                if (acceptance.isAccepted) {
                  complete(StatusCodes.Accepted)
                } else {
                  complete(StatusCodes.Conflict)
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

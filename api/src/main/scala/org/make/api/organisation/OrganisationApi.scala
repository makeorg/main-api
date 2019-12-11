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

package org.make.api.organisation

import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import com.sksamuel.elastic4s.searches.sort.SortOrder
import com.sksamuel.elastic4s.searches.sort.SortOrder.Desc
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.{
  ProposalServiceComponent,
  ProposalsResultSeededResponse,
  ProposalsResultWithUserVoteSeededResponse
}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.UserResponse
import org.make.core.auth.UserRights
import org.make.core.common.indexed.Sort
import org.make.core.operation.OperationKind
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId
import org.make.core.user.indexed.OrganisationSearchResult
import org.make.core.{HttpCodes, ParameterExtractors}
import scalaoauth2.provider.AuthInfo

import scala.collection.immutable

@Api(value = "Organisations")
@Path(value = "/organisations")
trait OrganisationApi extends Directives {

  @ApiOperation(value = "get-organisation", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "organisationId", paramType = "path", dataType = "string"))
  )
  @ApiResponses(value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[UserResponse])))
  @Path(value = "/{organisationId}")
  def getOrganisation: Route

  @ApiOperation(value = "get-organisations", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "organisationIds", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "organisationName", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "slug", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "country", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "language", paramType = "query", dataType = "string")
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OrganisationSearchResult]))
  )
  def getOrganisations: Route

  @ApiOperation(value = "get-organisation-proposals", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "organisationId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer")
    )
  )
  @ApiResponses(
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsResultSeededResponse]))
  )
  @Path(value = "/{organisationId}/proposals")
  def getOrganisationProposals: Route

  @ApiOperation(value = "get-organisation-votes", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "organisationId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(name = "votes", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "qualifications", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "sort", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "order", paramType = "query", dataType = "string"),
      new ApiImplicitParam(name = "limit", paramType = "query", dataType = "integer"),
      new ApiImplicitParam(name = "skip", paramType = "query", dataType = "integer")
    )
  )
  @ApiResponses(
    value = Array(
      new ApiResponse(
        code = HttpCodes.OK,
        message = "Ok",
        response = classOf[ProposalsResultWithUserVoteSeededResponse]
      )
    )
  )
  @Path(value = "/{organisationId}/votes")
  def getOrganisationVotes: Route

  def routes: Route = getOrganisation ~ getOrganisations ~ getOrganisationProposals ~ getOrganisationVotes

}

trait OrganisationApiComponent {
  def organisationApi: OrganisationApi
}

trait DefaultOrganisationApiComponent
    extends OrganisationApiComponent
    with MakeAuthenticationDirectives
    with StrictLogging
    with ParameterExtractors {

  this: OrganisationServiceComponent
    with ProposalServiceComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with MakeDataHandlerComponent
    with MakeSettingsComponent =>

  override lazy val organisationApi: OrganisationApi = new DefaultOrganisationApi

  class DefaultOrganisationApi extends OrganisationApi {
    val organisationId: PathMatcher1[UserId] = Segment.map(id => UserId(id))

    override def getOrganisation: Route =
      get {
        path("organisations" / organisationId) { organisationId =>
          makeOperation("GetOrganisation") { _ =>
            provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { user =>
              complete(UserResponse(user))
            }
          }
        }
      }

    override def getOrganisations: Route =
      get {
        path("organisations") {
          makeOperation("GetOrganisations") { _ =>
            parameters(
              (
                Symbol("organisationIds").as[immutable.Seq[UserId]].?,
                Symbol("organisationName").as[String].?,
                Symbol("slug").as[String].?,
                Symbol("country").as[Country].?,
                Symbol("language").as[Language].?
              )
            ) {
              (organisationIds: Option[Seq[UserId]],
               organisationName: Option[String],
               slug: Option[String],
               country: Option[Country],
               language: Option[Language]) =>
                provideAsync(organisationService.search(organisationName, slug, organisationIds, country, language)) {
                  results =>
                    complete(results)
                }
            }
          }
        }
      }

    override def getOrganisationProposals: Route =
      get {
        path("organisations" / organisationId / "proposals") { organisationId =>
          makeOperation("GetOrganisationProposals") { requestContext =>
            optionalMakeOAuth2 { optionalUserAuth: Option[AuthInfo[UserRights]] =>
              parameters(
                (Symbol("sort").?, Symbol("order").as[SortOrder].?, Symbol("limit").as[Int].?, Symbol("skip").as[Int].?)
              ) { (sort: Option[String], order: Option[SortOrder], limit: Option[Int], skip: Option[Int]) =>
                provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { _ =>
                  val defaultSort = Some("createdAt")
                  val defaultOrder = Some(Desc)
                  provideAsync(
                    proposalService.searchForUser(
                      optionalUserAuth.map(_.user.userId),
                      SearchQuery(
                        filters = Some(
                          SearchFilters(
                            user = Some(UserSearchFilter(organisationId)),
                            operationKinds = Some(
                              OperationKindsSearchFilter(
                                Seq(
                                  OperationKind.GreatCause,
                                  OperationKind.PublicConsultation,
                                  OperationKind.BusinessConsultation
                                )
                              )
                            )
                          )
                        ),
                        sort = Some(Sort(field = sort.orElse(defaultSort), mode = order.orElse(defaultOrder))),
                        limit = limit,
                        skip = skip
                      ),
                      requestContext
                    )
                  ) { listProposals =>
                    complete(listProposals)
                  }
                }
              }
            }
          }
        }
      }

    override def getOrganisationVotes: Route =
      get {
        path("organisations" / organisationId / "votes") { organisationId =>
          makeOperation("GetOrganisationVotes") { requestContext =>
            optionalMakeOAuth2 { userAuth: Option[AuthInfo[UserRights]] =>
              provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { _ =>
                parameters(
                  (
                    Symbol("votes").as[immutable.Seq[VoteKey]].?,
                    Symbol("qualifications").as[immutable.Seq[QualificationKey]].?,
                    Symbol("sort").?,
                    Symbol("order").as[SortOrder].?,
                    Symbol("limit").as[Int].?,
                    Symbol("skip").as[Int].?
                  )
                ) {
                  (votes: Option[Seq[VoteKey]],
                   qualifications: Option[Seq[QualificationKey]],
                   sort: Option[String],
                   order: Option[SortOrder],
                   limit: Option[Int],
                   skip: Option[Int]) =>
                    val defaultSort = Some("createdAt")
                    val defaultOrder = Some(Desc)
                    onSuccess(
                      organisationService.getVotedProposals(
                        organisationId = organisationId,
                        maybeUserId = userAuth.map(_.user.userId),
                        filterVotes = votes,
                        filterQualifications = qualifications,
                        sort = Some(Sort(field = sort.orElse(defaultSort), mode = order.orElse(defaultOrder))),
                        limit = limit,
                        skip = skip,
                        requestContext = requestContext
                      )
                    ) { proposals =>
                      complete(proposals)
                    }
                }
              }
            }
          }
        }
      }
  }
}

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

import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller._
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
import org.make.core.profile.Profile
import org.make.core.proposal.{SearchQuery, _}
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId
import org.make.core.{HttpCodes, Order, ParameterExtractors}
import scalaoauth2.provider.AuthInfo
import org.make.core.ApiParamMagnetHelper._

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

  @ApiOperation(value = "get-organisation-profile", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "organisationId", paramType = "path", dataType = "string"))
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OrganisationProfileResponse]))
  )
  @Path(value = "/{organisationId}/profile")
  def getOrganisationProfile: Route

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
    value =
      Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OrganisationsSearchResultResponse]))
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
      new ApiImplicitParam(
        name = "votes",
        paramType = "query",
        dataType = "string",
        allowableValues = "agree,disagree,neutral",
        allowMultiple = true
      ),
      new ApiImplicitParam(
        name = "qualifications",
        paramType = "query",
        dataType = "string",
        allowableValues =
          "likeIt,doable,platitudeAgree,noWay,impossible,platitudeDisagree,doNotUnderstand,noOpinion,doNotCare",
        allowMultiple = true
      ),
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

  @ApiOperation(
    value = "update-organisation-profile",
    httpMethod = "PUT",
    authorizations = Array(
      new Authorization(
        value = "MakeApi",
        scopes = Array(new AuthorizationScope(scope = "user", description = "application user"))
      )
    )
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[OrganisationProfileResponse]))
  )
  @ApiImplicitParams(
    value = Array(
      new ApiImplicitParam(name = "userId", paramType = "path", dataType = "string"),
      new ApiImplicitParam(
        value = "body",
        paramType = "body",
        dataType = "org.make.api.organisation.OrganisationProfileRequest"
      )
    )
  )
  @Path(value = "/{organisationId}/profile")
  def updateProfile: Route

  def routes: Route =
    getOrganisation ~ getOrganisations ~ getOrganisationProposals ~ getOrganisationVotes ~ getOrganisationProfile ~ updateProfile

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

    override def getOrganisationProfile: Route = {
      get {
        path("organisations" / organisationId / "profile") { organisationId =>
          makeOperation("GetOrganisationProfile") { _ =>
            provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { organisation =>
              complete(
                OrganisationProfileResponse(
                  organisationName = organisation.organisationName,
                  avatarUrl = organisation.profile.flatMap(_.avatarUrl),
                  description = organisation.profile.flatMap(_.description),
                  website = organisation.profile.flatMap(_.website),
                  optInNewsletter = organisation.profile.forall(_.optInNewsletter)
                )
              )
            }
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
                "organisationIds".as[Seq[UserId]].?,
                "organisationName".as[String].?,
                "slug".as[String].?,
                "country".as[Country].?,
                "language".as[Language].?
              )
            ) {
              (
                organisationIds: Option[Seq[UserId]],
                organisationName: Option[String],
                slug: Option[String],
                country: Option[Country],
                language: Option[Language]
              ) =>
                provideAsync(organisationService.search(organisationName, slug, organisationIds, country, language)) {
                  results =>
                    complete(OrganisationsSearchResultResponse.fromOrganisationSearchResult(results))
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
              parameters(("sort".?, "order".as[Order].?, "limit".as[Int].?, "skip".as[Int].?)) {
                (sort: Option[String], order: Option[Order], limit: Option[Int], skip: Option[Int]) =>
                  provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { _ =>
                    val defaultSort = Some("createdAt")
                    val defaultOrder = Some(Order.desc)
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
                          sort = Some(
                            Sort(field = sort.orElse(defaultSort), mode = order.orElse(defaultOrder).map(_.sortOrder))
                          ),
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
                    "votes".as[Seq[VoteKey]].*,
                    "qualifications".as[Seq[QualificationKey]].*,
                    "sort".?,
                    "order".as[Order].?,
                    "limit".as[Int].?,
                    "skip".as[Int].?
                  )
                ) {
                  (
                    votes: Option[Seq[VoteKey]],
                    qualifications: Option[Seq[QualificationKey]],
                    sort: Option[String],
                    order: Option[Order],
                    limit: Option[Int],
                    skip: Option[Int]
                  ) =>
                    val defaultSort = Some("createdAt")
                    val defaultOrder = Some(Order.desc)
                    onSuccess(
                      organisationService.getVotedProposals(
                        organisationId = organisationId,
                        maybeUserId = userAuth.map(_.user.userId),
                        filterVotes = votes,
                        filterQualifications = qualifications,
                        sort = Some(
                          Sort(field = sort.orElse(defaultSort), mode = order.orElse(defaultOrder).map(_.sortOrder))
                        ),
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

    override def updateProfile: Route = {
      put {
        path("organisations" / organisationId / "profile") { organisationId =>
          makeOperation("UpdateOrganisationProfile") { requestContext =>
            makeOAuth2 { user =>
              authorize(user.user.userId == organisationId) {
                decodeRequest {
                  entity(as[OrganisationProfileRequest]) { request =>
                    provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { organisation =>
                      val modifiedProfile = organisation.profile
                        .orElse(Profile.parseProfile())
                        .map(
                          _.copy(
                            avatarUrl = request.avatarUrl.map(_.value),
                            description = request.description,
                            website = request.website.map(_.value),
                            optInNewsletter = request.optInNewsletter
                          )
                        )

                      val modifiedOrganisation =
                        organisation.copy(profile = modifiedProfile, organisationName = Some(request.organisationName))

                      provideAsync(
                        organisationService
                          .update(
                            organisation = modifiedOrganisation,
                            moderatorId = None,
                            oldEmail = modifiedOrganisation.email,
                            requestContext = requestContext
                          )
                      ) { _ =>
                        complete(
                          OrganisationProfileResponse(
                            organisationName = modifiedOrganisation.organisationName,
                            avatarUrl = modifiedOrganisation.profile.flatMap(_.avatarUrl),
                            description = modifiedOrganisation.profile.flatMap(_.description),
                            website = modifiedOrganisation.profile.flatMap(_.website),
                            optInNewsletter = modifiedOrganisation.profile.forall(_.optInNewsletter)
                          )
                        )
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
  }
}

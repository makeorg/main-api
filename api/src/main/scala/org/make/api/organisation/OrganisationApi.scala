package org.make.api.organisation

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{PathMatcher1, Route}
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations._
import javax.ws.rs.Path
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.ProposalServiceComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.user.{OrganisationServiceComponent, UserResponse}
import org.make.core.HttpCodes
import org.make.core.auth.UserRights
import org.make.core.proposal._
import org.make.core.proposal.indexed.ProposalsSearchResult
import org.make.core.user.UserId
import scalaoauth2.provider.AuthInfo

import scala.util.Try

@Api(value = "Organisations")
@Path(value = "/organisations")
trait OrganisationApi extends MakeAuthenticationDirectives with StrictLogging {
  this: OrganisationServiceComponent
    with ProposalServiceComponent
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with MakeSettingsComponent =>

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

  @ApiOperation(value = "get-organisation-proposals", httpMethod = "GET", code = HttpCodes.OK)
  @ApiImplicitParams(
    value = Array(new ApiImplicitParam(name = "organisationId", paramType = "path", dataType = "string"))
  )
  @ApiResponses(
    value = Array(new ApiResponse(code = HttpCodes.OK, message = "Ok", response = classOf[ProposalsSearchResult]))
  )
  @Path(value = "/{organisationId}/proposals")
  def getOrganisationProposals: Route =
    get {
      path("organisations" / organisationId / "proposals") { organisationId =>
        makeOperation("GetOrganisationProposals") { requestContext =>
          optionalMakeOAuth2 { optionalUserAuth: Option[AuthInfo[UserRights]] =>
            provideAsyncOrNotFound(organisationService.getOrganisation(organisationId)) { organisation =>
              if (!organisation.isOrganisation) {
                complete(StatusCodes.Forbidden)
              } else {
                provideAsync(
                  proposalService.searchForUser(
                    optionalUserAuth.map(_.user.userId),
                    SearchQuery(filters = Some(SearchFilters(user = Some(UserSearchFilter(organisationId))))),
                    None,
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

  val organisationRoutes: Route = getOrganisation ~ getOrganisationProposals

  val organisationId: PathMatcher1[UserId] = Segment.flatMap(id => Try(UserId(id)).toOption)

}

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

import java.time.ZonedDateTime
import java.util.Date

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import com.sksamuel.elastic4s.searches.sort.SortOrder.Desc
import org.make.api.proposal._
import org.make.api.user.UserResponse
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.auth.UserRights
import org.make.core.common.indexed.Sort
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.VoteKey.Agree
import org.make.core.proposal._
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.user.Role.{RoleActor, RoleCitizen}
import org.make.core.user.indexed.{IndexedOrganisation, OrganisationSearchResult}
import org.make.core.user.{UserId, UserType}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.{ArgumentMatchers, Mockito}
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class OrganisationApiTest
    extends MakeApiTestBase
    with DefaultOrganisationApiComponent
    with ProposalServiceComponent
    with OrganisationServiceComponent
    with OrganisationSearchEngineComponent {

  override val proposalService: ProposalService = mock[ProposalService]
  override val organisationService: OrganisationService = mock[OrganisationService]
  override val elasticsearchOrganisationAPI: OrganisationSearchEngine = mock[OrganisationSearchEngine]

  private val validAccessToken = "my-valid-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)

  Mockito.when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  Mockito
    .when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              userId = UserId("user-citizen"),
              roles = Seq(RoleCitizen),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            Some("user"),
            None
          )
        )
      )
    )

  val routes: Route = sealRoute(organisationApi.routes)

  val now: ZonedDateTime = DateHelper.now()

  val returnedOrganisation = TestUtils.user(
    id = UserId("make-org"),
    email = "make@make.org",
    firstName = None,
    lastName = None,
    enabled = true,
    emailVerified = true,
    roles = Seq(RoleActor),
    organisationName = Some("Make.org"),
    userType = UserType.UserTypeOrganisation
  )

  Mockito
    .when(organisationService.getOrganisation(UserId("make-org")))
    .thenReturn(Future.successful(Some(returnedOrganisation)))

  Mockito
    .when(organisationService.getOrganisation(UserId("classic-user")))
    .thenReturn(Future.successful(None))

  Mockito
    .when(organisationService.getOrganisation(UserId("non-existant")))
    .thenReturn(Future.successful(None))

  val proposalsList = ProposalsResultSeededResponse(
    total = 4,
    results = Seq(
      ProposalResponse(
        id = ProposalId("proposal-1"),
        country = Country("FR"),
        language = Language("fr"),
        userId = UserId("make-org"),
        content = "blabla",
        slug = "blabla",
        createdAt = DateHelper.now(),
        updatedAt = Some(DateHelper.now()),
        votes = Seq.empty,
        context = Some(
          ProposalContextResponse(
            source = None,
            operation = None,
            location = None,
            question = None,
            getParameters = Seq.empty
          )
        ),
        trending = None,
        labels = Seq.empty,
        author = AuthorResponse(
          firstName = None,
          organisationName = None,
          organisationSlug = None,
          postalCode = None,
          age = None,
          avatarUrl = None,
          userType = None
        ),
        organisations = Seq.empty,
        themeId = None,
        tags = Seq.empty,
        status = ProposalStatus.Accepted,
        idea = Some(IdeaId("idea-id")),
        operationId = Some(OperationId("operation1")),
        myProposal = false,
        question = None,
        proposalKey = "pr0p0541k3y"
      ),
      ProposalResponse(
        id = ProposalId("proposal-2"),
        country = Country("FR"),
        language = Language("fr"),
        userId = UserId("make-org"),
        content = "blablabla",
        slug = "blablabla",
        createdAt = DateHelper.now(),
        updatedAt = Some(DateHelper.now()),
        votes = Seq.empty,
        context = Some(
          ProposalContextResponse(
            source = None,
            operation = None,
            location = None,
            question = None,
            getParameters = Seq.empty
          )
        ),
        trending = None,
        labels = Seq.empty,
        author = AuthorResponse(
          firstName = None,
          organisationName = None,
          organisationSlug = None,
          postalCode = None,
          age = None,
          avatarUrl = None,
          userType = None
        ),
        organisations = Seq.empty,
        themeId = None,
        tags = Seq.empty,
        status = ProposalStatus.Accepted,
        idea = Some(IdeaId("other-idea-id")),
        operationId = Some(OperationId("operation1")),
        myProposal = false,
        question = None,
        proposalKey = "pr0p0541k3y"
      ),
      ProposalResponse(
        id = ProposalId("proposal-3"),
        country = Country("FR"),
        language = Language("fr"),
        userId = UserId("make-org"),
        content = "blablabla",
        slug = "blablabla",
        createdAt = DateHelper.now(),
        updatedAt = Some(DateHelper.now()),
        votes = Seq.empty,
        context = Some(
          ProposalContextResponse(
            source = None,
            operation = None,
            location = None,
            question = None,
            getParameters = Seq.empty
          )
        ),
        trending = None,
        labels = Seq.empty,
        author = AuthorResponse(
          firstName = None,
          organisationName = None,
          organisationSlug = None,
          postalCode = None,
          age = None,
          avatarUrl = None,
          userType = None
        ),
        organisations = Seq.empty,
        themeId = None,
        tags = Seq.empty,
        status = ProposalStatus.Accepted,
        idea = Some(IdeaId("other-idea-id")),
        operationId = Some(OperationId("operation2")),
        myProposal = false,
        question = None,
        proposalKey = "pr0p0541k3y"
      ),
      ProposalResponse(
        id = ProposalId("proposal-4"),
        country = Country("FR"),
        language = Language("fr"),
        userId = UserId("make-org"),
        content = "blablabla",
        slug = "blablabla",
        createdAt = DateHelper.now(),
        updatedAt = Some(DateHelper.now()),
        votes = Seq.empty,
        context = Some(
          ProposalContextResponse(
            source = None,
            operation = None,
            location = None,
            question = None,
            getParameters = Seq.empty
          )
        ),
        trending = None,
        labels = Seq.empty,
        author = AuthorResponse(
          firstName = None,
          organisationName = None,
          organisationSlug = None,
          postalCode = None,
          age = None,
          avatarUrl = None,
          userType = None
        ),
        organisations = Seq.empty,
        themeId = Some(ThemeId("bar-theme")),
        tags = Seq.empty,
        status = ProposalStatus.Accepted,
        idea = Some(IdeaId("other-idea-id")),
        operationId = None,
        myProposal = false,
        question = None,
        proposalKey = "pr0p0541k3y"
      )
    ),
    None
  )

  Mockito
    .when(proposalService.searchForUser(any[Option[UserId]], any[SearchQuery], any[RequestContext]))
    .thenReturn(Future.successful(proposalsList))

  feature("get organisation") {
    scenario("get existing organisation") {
      Get("/organisations/make-org") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val organisation: UserResponse = entityAs[UserResponse]
        organisation.userId should be(UserId("make-org"))
      }
    }

    scenario("get non existing organisation") {
      Get("/organisations/non-existant") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("search organisations") {
    Mockito
      .when(
        organisationService
          .search(
            ArgumentMatchers.eq(Some("Make.org")),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None)
          )
      )
      .thenReturn(
        Future.successful(
          OrganisationSearchResult(1, Seq(IndexedOrganisation.createFromOrganisation(returnedOrganisation)))
        )
      )
    Mockito
      .when(
        organisationService
          .search(
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(Some("make-org")),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None)
          )
      )
      .thenReturn(
        Future.successful(
          OrganisationSearchResult(1, Seq(IndexedOrganisation.createFromOrganisation(returnedOrganisation)))
        )
      )
    Mockito
      .when(
        organisationService
          .search(
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(Some(Seq(UserId("make-org"), UserId("toto")))),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None)
          )
      )
      .thenReturn(
        Future.successful(
          OrganisationSearchResult(1, Seq(IndexedOrganisation.createFromOrganisation(returnedOrganisation)))
        )
      )
    Mockito
      .when(
        organisationService
          .search(
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(Some(Country("FR"))),
            ArgumentMatchers.eq(Some(Language("fr")))
          )
      )
      .thenReturn(
        Future.successful(
          OrganisationSearchResult(1, Seq(IndexedOrganisation.createFromOrganisation(returnedOrganisation)))
        )
      )
    scenario("search by organisation name") {
      Get("/organisations?organisationName=Make.org") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val organisationResults: OrganisationSearchResult = entityAs[OrganisationSearchResult]
        organisationResults.total should be(1)
        organisationResults.results.head.organisationId should be(UserId("make-org"))
      }
    }

    scenario("search by slug") {
      Get("/organisations?slug=make-org") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val organisationResults: OrganisationSearchResult = entityAs[OrganisationSearchResult]
        organisationResults.total should be(1)
        organisationResults.results.head.organisationId should be(UserId("make-org"))
      }
    }
    scenario("search by organisationIds") {
      Get("/organisations?organisationIds=make-org,toto") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val organisationResults: OrganisationSearchResult = entityAs[OrganisationSearchResult]
        organisationResults.total should be(1)
        organisationResults.results.head.organisationId should be(UserId("make-org"))
      }
    }
    scenario("search by organisation country") {
      Get("/organisations?country=FR&language=fr") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val organisationResults: OrganisationSearchResult = entityAs[OrganisationSearchResult]
        organisationResults.total should be(1)
        organisationResults.results.head.organisationId should be(UserId("make-org"))
      }
    }
  }

  feature("Get list of organisation proposals") {
    scenario("organisationId does not correspond to an organisation") {
      Get("/organisations/classic-user/proposals") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    scenario("search organisation proposals unauthenticated") {
      Get("/organisations/make-org/proposals") ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val proposalsSearchResult: ProposalsResultSeededResponse = entityAs[ProposalsResultSeededResponse]
        proposalsSearchResult.total shouldBe 4
        proposalsSearchResult.results.size shouldBe 4
        proposalsSearchResult.results.map(_.id) should contain(ProposalId("proposal-1"))
      }
    }

    scenario("search organisation proposals authenticated") {
      Get("/organisations/make-org/proposals")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val proposalsResultSeededResponse: ProposalsResultSeededResponse = entityAs[ProposalsResultSeededResponse]
        proposalsResultSeededResponse.total shouldBe 4
        proposalsResultSeededResponse.results.size shouldBe 4
        proposalsResultSeededResponse.results.map(_.id) should contain(ProposalId("proposal-1"))
      }
    }

    scenario("search ordered organisation proposals with uppercase order") {
      Get("/organisations/make-org/proposals?sort=createdAt&order=DESC")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val proposalsResultSeededResponse: ProposalsResultSeededResponse = entityAs[ProposalsResultSeededResponse]
        proposalsResultSeededResponse.total shouldBe 4
      }
    }
  }

  feature("get votes of an organisation") {

    val proposalListWithVote = ProposalsResultWithUserVoteSeededResponse(
      total = proposalsList.total,
      results = proposalsList.results.map(
        proposalResult =>
          ProposalResultWithUserVote(
            proposal = proposalResult,
            vote = Agree,
            voteDate = DateHelper.now(),
            voteDetails = None
        )
      ),
      seed = None
    )

    Mockito
      .when(
        organisationService.getVotedProposals(
          organisationId = ArgumentMatchers.any[UserId],
          maybeUserId = ArgumentMatchers.any[Option[UserId]],
          filterVotes = ArgumentMatchers.eq(None),
          filterQualifications = ArgumentMatchers.eq(None),
          sort = ArgumentMatchers.eq(Some(Sort(field = Some("createdAt"), mode = Some(Desc)))),
          limit = ArgumentMatchers.eq(None),
          skip = ArgumentMatchers.eq(None),
          requestContext = ArgumentMatchers.any[RequestContext]
        )
      )
      .thenReturn(Future.successful(proposalListWithVote))

    scenario("get proposals voted from existing organisation unauthenticated") {
      Get("/organisations/make-org/votes") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val votedProposals: ProposalsResultWithUserVoteSeededResponse =
          entityAs[ProposalsResultWithUserVoteSeededResponse]
        votedProposals.total should be(4)
      }
    }

    scenario("get proposals voted from existing organisation authenticated") {
      Get("/organisations/make-org/votes")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val votedProposals: ProposalsResultWithUserVoteSeededResponse =
          entityAs[ProposalsResultWithUserVoteSeededResponse]
        votedProposals.total should be(4)
      }
    }

    scenario("get proposals voted from non organisation user") {
      Mockito
        .when(organisationService.getOrganisation(ArgumentMatchers.any[UserId]))
        .thenReturn(Future.successful(None))
      Get("/organisations/make-org/votes") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

}

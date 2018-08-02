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
import org.make.api.MakeApiTestBase
import org.make.api.proposal.{ProposalResult, ProposalService, ProposalServiceComponent, ProposalsResultSeededResponse}
import org.make.api.user.{OrganisationService, OrganisationServiceComponent, UserResponse}
import org.make.core.auth.UserRights
import org.make.core.idea.IdeaId
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.user.Role.{RoleActor, RoleCitizen}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.{ArgumentMatchers, Mockito}
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class OrganisationApiTest
    extends MakeApiTestBase
    with OrganisationApi
    with ProposalServiceComponent
    with OrganisationServiceComponent {

  override val proposalService: ProposalService = mock[ProposalService]
  override val organisationService: OrganisationService = mock[OrganisationService]

  private val validAccessToken = "my-valid-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)

  Mockito.when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  Mockito
    .when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(Some(AuthInfo(UserRights(UserId("user-citizen"), Seq(RoleCitizen)), None, Some("user"), None)))
    )

  val routes: Route = sealRoute(organisationRoutes)

  val now: ZonedDateTime = DateHelper.now()

  val returnedOrganisation = User(
    userId = UserId("make-org"),
    email = "make@make.org",
    firstName = None,
    lastName = None,
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    isOrganisation = true,
    lastConnection = now,
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(RoleActor),
    country = Country("FR"),
    language = Language("fr"),
    profile = None,
    createdAt = None,
    updatedAt = None,
    lastMailingError = None
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
    total = 2,
    results = Seq(
      ProposalResult(
        id = ProposalId("proposal-1"),
        country = Country("FR"),
        language = Language("fr"),
        userId = UserId("make-org"),
        content = "blabla",
        slug = "blabla",
        createdAt = ZonedDateTime.now(),
        updatedAt = Some(ZonedDateTime.now()),
        votes = Seq.empty,
        context = Some(Context(source = None, operation = None, location = None, question = None)),
        trending = None,
        labels = Seq.empty,
        author = Author(firstName = None, organisationName = None, postalCode = None, age = None, avatarUrl = None),
        organisations = Seq.empty,
        themeId = Some(ThemeId("foo-theme")),
        tags = Seq.empty,
        status = ProposalStatus.Accepted,
        idea = Some(IdeaId("idea-id")),
        operationId = None,
        myProposal = false
      ),
      ProposalResult(
        id = ProposalId("proposal-2"),
        country = Country("FR"),
        language = Language("fr"),
        userId = UserId("make-org"),
        content = "blablabla",
        slug = "blablabla",
        createdAt = ZonedDateTime.now(),
        updatedAt = Some(ZonedDateTime.now()),
        votes = Seq.empty,
        context = Some(Context(source = None, operation = None, location = None, question = None)),
        trending = None,
        labels = Seq.empty,
        author = Author(firstName = None, organisationName = None, postalCode = None, age = None, avatarUrl = None),
        organisations = Seq.empty,
        themeId = Some(ThemeId("bar-theme")),
        tags = Seq.empty,
        status = ProposalStatus.Accepted,
        idea = Some(IdeaId("other-idea-id")),
        operationId = None,
        myProposal = false
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
        proposalsSearchResult.total shouldBe 2
        proposalsSearchResult.results.size shouldBe 2
        proposalsSearchResult.results.exists(_.id == ProposalId("proposal-1")) shouldBe true
        proposalsSearchResult.results.exists(_.id == ProposalId("proposal-2")) shouldBe true
      }
    }

    scenario("search organisation proposals authenticated") {
      Get("/organisations/make-org/proposals")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
        val proposalsResultSeededResponse: ProposalsResultSeededResponse = entityAs[ProposalsResultSeededResponse]
        proposalsResultSeededResponse.total shouldBe 2
        proposalsResultSeededResponse.results.size shouldBe 2
        proposalsResultSeededResponse.results.exists(_.id == ProposalId("proposal-1")) shouldBe true
        proposalsResultSeededResponse.results.exists(_.id == ProposalId("proposal-2")) shouldBe true
      }
    }
  }

  feature("get votes of an organisation") {
    Mockito
      .when(
        organisationService.getVotedProposals(
          ArgumentMatchers.any[UserId],
          ArgumentMatchers.any[Option[UserId]],
          ArgumentMatchers.eq(None),
          ArgumentMatchers.eq(None),
          ArgumentMatchers.any[RequestContext]
        )
      )
      .thenReturn(Future.successful(ProposalsResultSeededResponse(2, Seq.empty, None)))
    scenario("get proposals voted from existing organisation unauthenticated") {
      Get("/organisations/make-org/votes") ~> routes ~> check {
        status should be(StatusCodes.OK)
        val votedProposals: ProposalsResultSeededResponse = entityAs[ProposalsResultSeededResponse]
        votedProposals.total should be(2)
      }
    }

    scenario("get proposals voted from existing organisation authenticated") {
      Get("/organisations/make-org/votes")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val votedProposals: ProposalsResultSeededResponse = entityAs[ProposalsResultSeededResponse]
        votedProposals.total should be(2)
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

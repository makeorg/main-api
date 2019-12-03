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

import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical._
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.user.Role.{RoleActor, RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.{User, UserId, UserType}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class ModerationOrganisationApiTest
    extends MakeApiTestBase
    with DefaultModerationOrganisationApiComponent
    with OrganisationServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent {

  override val organisationService: OrganisationService = mock[OrganisationService]

  val routes: Route = sealRoute(moderationOrganisationApi.routes)

  val fakeOrganisation = TestUtils.user(
    id = UserId("ABCD"),
    email = "foo@bar.com",
    firstName = None,
    lastName = None,
    organisationName = Some("JohnDoe Corp."),
    lastIp = Some("127.0.0.1"),
    hashedPassword = Some("passpass"),
    enabled = true,
    emailVerified = true,
    roles = Seq(RoleActor),
    userType = UserType.UserTypeOrganisation
  )

  val validAccessToken = "my-valid-access-token"
  val adminToken = "my-admin-access-token"
  val moderatorToken = "my-moderator-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)
  private val adminAccessToken = AccessToken(adminToken, None, None, Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken = AccessToken(moderatorToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
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

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            UserRights(
              UserId("user-admin"),
              roles = Seq(RoleAdmin),
              availableQuestions = Seq.empty,
              emailVerified = true
            ),
            None,
            None,
            None
          )
        )
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future
        .successful(
          Some(
            AuthInfo(
              UserRights(
                UserId("user-moderator"),
                roles = Seq(RoleModerator),
                availableQuestions = Seq.empty,
                emailVerified = true
              ),
              None,
              None,
              None
            )
          )
        )
    )

  feature("register organisation") {
    scenario("register organisation unauthenticate") {
      Given("a unauthenticate user")
      When("I want to register an organisation")
      Then("I should get an unauthorized error")
      Post("/moderation/organisations").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("register organisation without admin rights") {
      Given("a non admin user")
      When("I want to register an organisation")
      Then("I should get a forbidden error")
      Post("/moderation/organisations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("register organisation with admin rights") {
      Given("a admin user")
      When("I want to register an organisation")
      Then("I should get a Created status")
      when(organisationService.register(any[OrganisationRegisterData], any[RequestContext]))
        .thenReturn(Future.successful(fakeOrganisation))
      Post("/moderation/organisations")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            """{"organisationName": "orga", "email": "bar@foo.com", "password": "azertyui"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    scenario("register organisation with admin rights without password") {
      Given("a admin user")
      When("I want to register an organisation without a password")
      Then("I should get a Created status")
      when(organisationService.register(any[OrganisationRegisterData], any[RequestContext]))
        .thenReturn(Future.successful(fakeOrganisation))
      Post("/moderation/organisations")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"organisationName": "orga", "email": "bar@foo.com"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    scenario("register organisation without organisation name") {
      Given("a admin user")
      When("I want to register an organisation")
      Then("I should get a BadRequest status")
      Post("/moderation/organisations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"email": "bar@foo.com", "password": "azertyui"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    scenario("register organisation with avatarUrl too long") {
      Given("a admin user")
      When("I want to register an organisation with a too long avatarUrl")
      Then("I should get a BadRequest status")
      val longAvatarUrl = "http://example.com/" + "a" * 2048
      Post("/moderation/organisations")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"email": "bar@foo.com", "password": "azertyui", "organisationName":"azer","avatarUrl":"$longAvatarUrl"}""".stripMargin
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  feature("get organisation") {

    when(organisationService.getOrganisation(matches(UserId("ABCD"))))
      .thenReturn(Future.successful(Some(fakeOrganisation)))

    when(organisationService.getOrganisation(matches(UserId("non-existant"))))
      .thenReturn(Future.successful(None))

    scenario("get moderation organisation unauthenticate") {
      Get("/moderation/organisations/ABCD") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get moderation organisation without admin rights") {
      Get("/moderation/organisations/ABCD")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get existing organisation") {
      Get("/moderation/organisations/ABCD")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val organisation: OrganisationResponse = entityAs[OrganisationResponse]
        organisation.id should be(UserId("ABCD"))
      }
    }

    scenario("get non existing organisation") {
      Get("/moderation/organisations/non-existant")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("update operation") {
    scenario("update organisation unauthenticate") {
      Given("a unauthenticate user")
      When("I want to update an organisation")
      Then("I should get an unauthorized error")
      Put("/moderation/organisations/ABCD")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("update organisation without admin rights") {
      Given("a non admin user")
      When("I want to update an organisation")
      Then("I should get a not found error")
      Put("/moderation/organisations/ABCD")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("update organisation with admin rights") {
      Given("a admin user")
      When("I want to update an organisation")
      Then("I should get a OK status")
      when(organisationService.getOrganisation(any[UserId])).thenReturn(Future.successful(Some(fakeOrganisation)))
      when(organisationService.update(any[User], any[Option[String]], any[RequestContext]))
        .thenReturn(Future.successful(fakeOrganisation.userId))
      Put("/moderation/organisations/ABCD")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"organisationName": "orga"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    scenario("update non organisation user") {
      Given("a admin user")
      When("I want to update a non organisation user")
      Then("I should get a Forbidden status")
      when(organisationService.getOrganisation(any[UserId]))
        .thenReturn(Future.successful(None))
      Put("/moderation/organisations/ABCD")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"organisationName": "orga"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    scenario("update non existing organisation") {
      Given("a admin user")
      When("I want to update a non existing organisation")
      Then("I should get a NotFound status")
      when(organisationService.getOrganisation(any[UserId]))
        .thenReturn(Future.successful(None))
      Put("/moderation/organisations/ABCD")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"organisationName": "orga"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  feature("Get organisations") {
    scenario("get organisations unauthenticated") {
      Given("a unauthenticate user")
      When("I want to get organisations")
      Then("I should get an unauthorized error")
      Get("/moderation/organisations") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    scenario("get organisations without moderation role") {
      Given("a non admin user")
      When("I want to get organisations")
      Then("I should get a forbidden error")
      Get("/moderation/organisations")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get organisations with moderation role") {

      when(organisationService.count()).thenReturn(Future.successful(1))

      Given("a moderator")
      When("I want to get organisations")
      Then("I should get a forbidden status")
      when(organisationService.find(any[Int], any[Option[Int]], any[Option[String]], any[Option[String]]))
        .thenReturn(Future.successful(Seq(fakeOrganisation)))
      Get("/moderation/organisations")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    scenario("get organisations with admin role") {

      when(organisationService.count()).thenReturn(Future.successful(1))

      Given("a moderator")
      When("I want to get organisations")
      Then("I should get an OK status")
      when(organisationService.find(any[Int], any[Option[Int]], any[Option[String]], any[Option[String]]))
        .thenReturn(Future.successful(Seq(fakeOrganisation)))
      Get("/moderation/organisations")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }
}

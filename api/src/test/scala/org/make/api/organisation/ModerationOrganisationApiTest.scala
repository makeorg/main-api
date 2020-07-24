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

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical._
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.{MakeApiTestBase, TestUtils}
import org.make.core.RequestContext
import org.make.core.user.Role.RoleActor
import org.make.core.user.{User, UserId, UserType}

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

  Feature("register organisation") {
    Scenario("register organisation unauthenticate") {
      Given("a unauthenticate user")
      When("I want to register an organisation")
      Then("I should get an unauthorized error")
      Post("/moderation/organisations").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("register organisation without admin rights") {
      Given("a non admin user")
      When("I want to register an organisation")
      Then("I should get a forbidden error")
      Post("/moderation/organisations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("register organisation with admin rights") {
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
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    Scenario("register organisation with admin rights without password") {
      Given("a admin user")
      When("I want to register an organisation without a password")
      Then("I should get a Created status")
      when(organisationService.register(any[OrganisationRegisterData], any[RequestContext]))
        .thenReturn(Future.successful(fakeOrganisation))
      Post("/moderation/organisations")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{"organisationName": "orga", "email": "bar@foo.com"}""")
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.Created
      }
    }

    Scenario("register organisation without organisation name") {
      Given("a admin user")
      When("I want to register an organisation")
      Then("I should get a BadRequest status")
      Post("/moderation/organisations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"email": "bar@foo.com", "password": "azertyui"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    Scenario("register organisation with avatarUrl too long") {
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
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }

  Feature("get organisation") {

    when(organisationService.getOrganisation(eqTo(UserId("ABCD"))))
      .thenReturn(Future.successful(Some(fakeOrganisation)))

    when(organisationService.getOrganisation(eqTo(UserId("non-existant"))))
      .thenReturn(Future.successful(None))

    Scenario("get moderation organisation unauthenticate") {
      Get("/moderation/organisations/ABCD") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get moderation organisation without admin rights") {
      Get("/moderation/organisations/ABCD")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get existing organisation") {
      Get("/moderation/organisations/ABCD")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val organisation: OrganisationResponse = entityAs[OrganisationResponse]
        organisation.id should be(UserId("ABCD"))
      }
    }

    Scenario("get non existing organisation") {
      Get("/moderation/organisations/non-existant")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  Feature("update operation") {
    Scenario("update organisation unauthenticate") {
      Given("a unauthenticate user")
      When("I want to update an organisation")
      Then("I should get an unauthorized error")
      Put("/moderation/organisations/ABCD")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("update organisation without admin rights") {
      Given("a non admin user")
      When("I want to update an organisation")
      Then("I should get a not found error")
      Put("/moderation/organisations/ABCD")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("update organisation with admin rights") {
      Given("a admin user")
      When("I want to update an organisation")
      Then("I should get a OK status")
      when(organisationService.getOrganisation(any[UserId])).thenReturn(Future.successful(Some(fakeOrganisation)))
      when(
        organisationService
          .update(any[User], any[Option[UserId]], any[String], any[RequestContext])
      ).thenReturn(Future.successful(fakeOrganisation.userId))
      Put("/moderation/organisations/ABCD")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"organisationName": "orga"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

    Scenario("update non organisation user") {
      Given("a admin user")
      When("I want to update a non organisation user")
      Then("I should get a Forbidden status")
      when(organisationService.getOrganisation(any[UserId]))
        .thenReturn(Future.successful(None))
      Put("/moderation/organisations/ABCD")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"organisationName": "orga"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    Scenario("update non existing organisation") {
      Given("a admin user")
      When("I want to update a non existing organisation")
      Then("I should get a NotFound status")
      when(organisationService.getOrganisation(any[UserId]))
        .thenReturn(Future.successful(None))
      Put("/moderation/organisations/ABCD")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"organisationName": "orga"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }
  }

  Feature("Get organisations") {
    Scenario("get organisations unauthenticated") {
      Given("a unauthenticate user")
      When("I want to get organisations")
      Then("I should get an unauthorized error")
      Get("/moderation/organisations") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("get organisations without moderation role") {
      Given("a non admin user")
      When("I want to get organisations")
      Then("I should get a forbidden error")
      Get("/moderation/organisations")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get organisations with moderation role") {

      when(organisationService.count(None)).thenReturn(Future.successful(1))

      Given("a moderator")
      When("I want to get organisations")
      Then("I should get a forbidden status")
      when(
        organisationService
          .find(any[Int], any[Option[Int]], any[Option[String]], any[Option[String]], any[Option[String]])
      ).thenReturn(Future.successful(Seq(fakeOrganisation)))
      Get("/moderation/organisations")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("get organisations with admin role") {

      when(organisationService.count(None)).thenReturn(Future.successful(1))

      Given("a moderator")
      When("I want to get organisations")
      Then("I should get an OK status")
      when(
        organisationService
          .find(any[Int], any[Option[Int]], any[Option[String]], any[Option[String]], any[Option[String]])
      ).thenReturn(Future.successful(Seq(fakeOrganisation)))
      Get("/moderation/organisations")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }
}

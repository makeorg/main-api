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

package org.make.api.technical.auth

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestBase
import org.make.core.auth.ClientId
import org.make.core.user.{CustomRole, UserId}

import scala.concurrent.Future

class AdminClientApiTest extends MakeApiTestBase with DefaultAdminClientApiComponent with ClientServiceComponent {

  override val clientService: ClientService = mock[ClientService]

  val routes: Route = sealRoute(adminClientApi.routes)

  Feature("create a client") {
    val validClient = client(clientId = ClientId("apiclient"), name = "client", secret = Some("secret"))

    when(
      clientService
        .createClient(
          name = eqTo("client"),
          allowedGrantTypes = eqTo(Seq("grant_type")),
          secret = eqTo(Some("secret")),
          scope = eqTo(Some("scope")),
          redirectUri = eqTo(Some("http://redirect-uri.com")),
          defaultUserId = eqTo(Some(UserId("123456-12345"))),
          roles = eqTo(Seq(CustomRole("role_custom"), CustomRole("role_default"))),
          tokenExpirationSeconds = eqTo(300),
          refreshExpirationSeconds = eqTo(400),
          reconnectExpirationSeconds = eqTo(900)
        )
    ).thenReturn(Future.successful(validClient))

    Scenario("unauthorize unauthenticated") {
      Post("/admin/clients") ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Post("/admin/clients")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Post("/admin/clients")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Post("/admin/clients")
          .withEntity(HttpEntity(ContentTypes.`application/json`, """{
              |  "name" : "client",
              |  "secret" : "secret",
              |  "allowedGrantTypes" : ["grant_type"],
              |  "scope" : "scope",
              |  "redirectUri" : "http://redirect-uri.com",
              |  "defaultUserId" : "123456-12345",
              |  "roles" : ["role_custom","role_default"],
              |  "tokenExpirationSeconds": 300,
              |  "refreshExpirationSeconds": 400,
              |  "reconnectExpirationSeconds": 900
              |}""".stripMargin))
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.Created)
        }
      }
    }
  }

  Feature("get a client") {
    val readClient = client(clientId = ClientId("apiclient"), name = "client", secret = Some("secret"))

    when(clientService.getClient(eqTo(readClient.clientId)))
      .thenReturn(Future.successful(Some(readClient)))
    when(clientService.getClient(eqTo(ClientId("fake-client"))))
      .thenReturn(Future.successful(None))

    Scenario("unauthorize unauthenticated") {
      Get("/admin/clients/apiclient") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Get("/admin/clients/apiclient").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Get("/admin/clients/apiclient").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing oauth client") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/clients/apiclient")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val client: ClientResponse = entityAs[ClientResponse]
          client.clientId should be(client.clientId)
          client.name should be(client.name)
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing oauth client") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Get("/admin/clients/fake-client")
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }

  Feature("update a client") {
    val clientBeforeUpdate = client(
      clientId = ClientId("apiclient"),
      name = "client",
      allowedGrantTypes = Seq("first_grant_type", "second_grant_type"),
      secret = Some("secret")
    )

    val updatedClient = client(
      clientId = ClientId("apiclient"),
      name = "updated client",
      allowedGrantTypes = Seq("first_grant_type", "second_grant_type"),
      secret = Some("secret")
    )

    when(clientService.getClient(eqTo(clientBeforeUpdate.clientId)))
      .thenReturn(Future.successful(Some(clientBeforeUpdate)))
    when(
      clientService.updateClient(
        eqTo(ClientId("apiclient")),
        eqTo("updated client"),
        eqTo(Seq("first_grant_type", "second_grant_type")),
        eqTo(Some("secret")),
        eqTo(None),
        eqTo(None),
        eqTo(None),
        eqTo(Seq.empty),
        eqTo(300),
        eqTo(400),
        eqTo(900)
      )
    ).thenReturn(Future.successful(Some(updatedClient)))
    when(
      clientService.updateClient(
        eqTo(ClientId("fake-client")),
        any[String],
        any[Seq[String]],
        any[Option[String]],
        any[Option[String]],
        any[Option[String]],
        any[Option[UserId]],
        any[Seq[CustomRole]],
        any[Int],
        any[Int],
        any[Int]
      )
    ).thenReturn(Future.successful(None))

    Scenario("unauthorize unauthenticated") {
      Put("/admin/clients/apiclient") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbid authenticated citizen") {
      Put("/admin/clients/apiclient")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbid authenticated moderator") {
      Put("/admin/clients/apiclient")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("allow authenticated admin on existing oauth client") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/clients/apiclient")
          .withEntity(
            HttpEntity(
              ContentTypes.`application/json`,
              """{
                                               | "name" : "updated client",
                                               |  "secret" : "secret",
                                               |  "allowedGrantTypes" : ["first_grant_type","second_grant_type"],
                                               |  "scope" : null,
                                               |  "redirectUri" : null,
                                               |  "defaultUserId" : null,
                                               |  "roles" : [],
                                               |  "tokenExpirationSeconds": 300,
                                               |  "refreshExpirationSeconds": 400,
                                               |  "reconnectExpirationSeconds": 900
                                               |}""".stripMargin
            )
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.OK)
          val client: ClientResponse = entityAs[ClientResponse]
          client.clientId should be(updatedClient.clientId)
          client.name should be(updatedClient.name)
        }
      }
    }

    Scenario("not found and allow authenticated admin on a non existing oauth client") {
      for (token <- Seq(tokenAdmin, tokenSuperAdmin)) {
        Put("/admin/clients/fake-client")
          .withEntity(
            HttpEntity(
              ContentTypes.`application/json`,
              """{
                                               |  "name" : "fake-client",
                                               |  "secret" : "secret",
                                               |  "allowedGrantTypes" : ["first_grant_type","second_grant_type"],
                                               |  "scope" : null,
                                               |  "redirectUri" : null,
                                               |  "defaultUserId" : null,
                                               |  "roles" : [],
                                               |  "tokenExpirationSeconds": 300,
                                               |  "refreshExpirationSeconds": 400,
                                               |  "reconnectExpirationSeconds": 900
                                               |}""".stripMargin
            )
          )
          .withHeaders(Authorization(OAuth2BearerToken(token))) ~> routes ~> check {
          status should be(StatusCodes.NotFound)
        }
      }
    }
  }
}

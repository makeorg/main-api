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
import org.make.core.auth.{Client, ClientId}
import org.make.core.user.{CustomRole, UserId}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when

import scala.concurrent.Future

class AdminClientApiTest extends MakeApiTestBase with DefaultAdminClientApiComponent with ClientServiceComponent {

  override val clientService: ClientService = mock[ClientService]

  val routes: Route = sealRoute(adminClientApi.routes)

  feature("create a client") {
    val validClient = Client(
      clientId = ClientId("apiclient"),
      name = "client",
      allowedGrantTypes = Seq.empty,
      secret = Some("secret"),
      scope = None,
      redirectUri = None,
      defaultUserId = None,
      roles = Seq.empty,
      tokenExpirationSeconds = 300
    )

    when(
      clientService
        .createClient(
          name = ArgumentMatchers.eq("client"),
          allowedGrantTypes = ArgumentMatchers.eq(Seq("grant_type")),
          secret = ArgumentMatchers.eq(Some("secret")),
          scope = ArgumentMatchers.eq(Some("scope")),
          redirectUri = ArgumentMatchers.eq(Some("http://redirect-uri.com")),
          defaultUserId = ArgumentMatchers.eq(Some(UserId("123456-12345"))),
          roles = ArgumentMatchers.eq(Seq(CustomRole("role_custom"), CustomRole("role_default"))),
          tokenExpirationSeconds = ArgumentMatchers.eq(300)
        )
    ).thenReturn(Future.successful(validClient))

    scenario("unauthorize unauthenticated") {
      Post("/admin/clients") ~>
        routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Post("/admin/clients")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbid authenticated moderator") {
      Post("/admin/clients")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated admin") {
      Post("/admin/clients")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
              |  "name" : "client",
              |  "secret" : "secret",
              |  "allowedGrantTypes" : ["grant_type"],
              |  "scope" : "scope",
              |  "redirectUri" : "http://redirect-uri.com",
              |  "defaultUserId" : "123456-12345",
              |  "roles" : ["role_custom","role_default"],
              |  "tokenExpirationSeconds": 300
              |}""".stripMargin))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }
  }

  feature("get a client") {
    val client = Client(
      clientId = ClientId("apiclient"),
      name = "client",
      allowedGrantTypes = Seq.empty,
      secret = Some("secret"),
      scope = None,
      redirectUri = None,
      defaultUserId = None,
      roles = Seq.empty,
      tokenExpirationSeconds = 300
    )

    when(clientService.getClient(ArgumentMatchers.eq(client.clientId)))
      .thenReturn(Future.successful(Some(client)))
    when(clientService.getClient(ArgumentMatchers.eq(ClientId("fake-client"))))
      .thenReturn(Future.successful(None))

    scenario("unauthorize unauthenticated") {
      Get("/admin/clients/apiclient") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Get("/admin/clients/apiclient").withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbid authenticated moderator") {
      Get("/admin/clients/apiclient").withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated admin on existing oauth client") {
      Get("/admin/clients/apiclient")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val client: ClientResponse = entityAs[ClientResponse]
        client.clientId should be(client.clientId)
        client.name should be(client.name)
      }
    }

    scenario("not found and allow authenticated admin on a non existing oauth client") {
      Get("/admin/clients/fake-client")
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }

  feature("update a client") {
    val client = Client(
      clientId = ClientId("apiclient"),
      name = "client",
      allowedGrantTypes = Seq("first_grant_type", "second_grant_type"),
      secret = Some("secret"),
      scope = None,
      redirectUri = None,
      defaultUserId = None,
      roles = Seq.empty,
      tokenExpirationSeconds = 300
    )
    val updatedClient = Client(
      clientId = ClientId("apiclient"),
      name = "updated client",
      allowedGrantTypes = Seq("first_grant_type", "second_grant_type"),
      secret = Some("secret"),
      scope = None,
      redirectUri = None,
      defaultUserId = None,
      roles = Seq.empty,
      tokenExpirationSeconds = 300
    )

    when(clientService.getClient(ArgumentMatchers.eq(client.clientId)))
      .thenReturn(Future.successful(Some(client)))
    when(
      clientService.updateClient(
        ArgumentMatchers.eq(ClientId("apiclient")),
        ArgumentMatchers.eq("updated client"),
        ArgumentMatchers.eq(Seq("first_grant_type", "second_grant_type")),
        ArgumentMatchers.eq(Some("secret")),
        ArgumentMatchers.eq(None),
        ArgumentMatchers.eq(None),
        ArgumentMatchers.eq(None),
        ArgumentMatchers.eq(Seq.empty),
        ArgumentMatchers.eq(300)
      )
    ).thenReturn(Future.successful(Some(updatedClient)))
    when(
      clientService.updateClient(
        ArgumentMatchers.eq(ClientId("fake-client")),
        ArgumentMatchers.any[String],
        ArgumentMatchers.any[Seq[String]],
        ArgumentMatchers.any[Option[String]],
        ArgumentMatchers.any[Option[String]],
        ArgumentMatchers.any[Option[String]],
        ArgumentMatchers.any[Option[UserId]],
        ArgumentMatchers.any[Seq[CustomRole]],
        ArgumentMatchers.any[Int]
      )
    ).thenReturn(Future.successful(None))

    scenario("unauthorize unauthenticated") {
      Put("/admin/clients/apiclient") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("forbid authenticated citizen") {
      Put("/admin/clients/apiclient")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("forbid authenticated moderator") {
      Put("/admin/clients/apiclient")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("allow authenticated admin on existing oauth client") {
      Put("/admin/clients/apiclient")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{
                                               | "name" : "updated client",
                                               |  "secret" : "secret",
                                               |  "allowedGrantTypes" : ["first_grant_type","second_grant_type"],
                                               |  "scope" : null,
                                               |  "redirectUri" : null,
                                               |  "defaultUserId" : null,
                                               |  "roles" : [],
                                               |  "tokenExpirationSeconds": 300
                                               |}""".stripMargin)
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val client: ClientResponse = entityAs[ClientResponse]
        client.clientId should be(updatedClient.clientId)
        client.name should be(updatedClient.name)
      }
    }

    scenario("not found and allow authenticated admin on a non existing oauth client") {
      Put("/admin/clients/fake-client")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, """{
                                               |  "name" : "fake-client",
                                               |  "secret" : "secret",
                                               |  "allowedGrantTypes" : ["first_grant_type","second_grant_type"],
                                               |  "scope" : null,
                                               |  "redirectUri" : null,
                                               |  "defaultUserId" : null,
                                               |  "roles" : [],
                                               |  "tokenExpirationSeconds": 300
                                               |}""".stripMargin)
        )
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }
  }
}

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

package org.make.api.technical.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.make.api.technical.auth.ClientService.ClientError
import org.make.api.technical.auth.{ClientErrorCode, ClientService, ClientServiceComponent}
import org.make.api.{MakeApi, MakeUnitTest}
import org.make.core.auth.{Client, ClientId}

import scala.concurrent.Future

class ClientDirectivesTest
    extends MakeUnitTest
    with ScalatestRouteTest
    with ClientServiceComponent
    with ClientDirectives {
  override val clientService: ClientService = mock[ClientService]

  val clients: Seq[Client] = Seq(
    client(clientId = ClientId("private"), secret = Some("secret")),
    client(clientId = ClientId("public"), secret = None)
  )

  when(clientService.getDefaultClient()).thenReturn(Future.successful(Right(client(ClientId("default")))))

  when(clientService.getClient(any[ClientId], any[Option[String]])).thenAnswer {
    (clientId: ClientId, secret: Option[String]) =>
      clients.find(_.clientId == clientId) match {
        case None => Future.successful(Left(ClientError(ClientErrorCode.UnknownClient, "Unknown")))
        case Some(client) =>
          if (client.secret == secret) {
            Future.successful(Right(client))
          } else {
            Future.successful(Left(ClientError(ClientErrorCode.BadCredentials, "")))
          }
      }
  }

  val route: Route = Route.seal(handleRejections(MakeApi.rejectionHandler) {
    handleExceptions(MakeApi.exceptionHandler("test", "test")) {
      get {
        path("no-client") {
          extractClient {
            case None        => complete(StatusCodes.OK)
            case Some(other) => complete(StatusCodes.BadRequest -> s"Expected no client, got ${other.toString}")
          }
        } ~
          path("client") {
            extractClient {
              case None => complete(StatusCodes.BadRequest)
              case _    => complete(StatusCodes.OK)
            }
          } ~
          path("with-default") {
            extractClientOrDefault { client =>
              complete(StatusCodes.OK -> client.clientId.value)
            }
          }
      }
    }
  })

  Feature("extract client") {
    Scenario("No client provided") {
      Get("/no-client") ~> route ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("private client") {
      Get("/client") ~> Authorization(BasicHttpCredentials("private", "secret")) ~> route ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("public client") {
      Get("/client") ~> Authorization(BasicHttpCredentials("public", "")) ~> route ~> check {
        status should be(StatusCodes.OK)
      }
    }

    Scenario("password mismatch on public") {
      Get("/client") ~> Authorization(BasicHttpCredentials("public", "secret")) ~> route ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("password mismatch on private") {
      Get("/client") ~> Authorization(BasicHttpCredentials("private", "")) ~> route ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
  }

  Feature("extract client or default") {
    Scenario("default client") {
      Get("/with-default") ~> route ~> check {
        status should be(StatusCodes.OK)
        responseAs[String] should be("default")
      }
    }

    Scenario("private client") {
      Get("/with-default") ~> Authorization(BasicHttpCredentials("private", "secret")) ~> route ~> check {
        status should be(StatusCodes.OK)
        responseAs[String] should be("private")
      }
    }
  }
}

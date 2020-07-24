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

package org.make.api.technical.security

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.{ActorSystemComponent, MakeApiTestBase}
import org.make.core.user.UserId

class SecurityApiTest
    extends MakeApiTestBase
    with MakeAuthenticationDirectives
    with DefaultSecurityApiComponent
    with DefaultSecurityConfigurationComponent
    with ActorSystemComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with MakeSettingsComponent {

  override val actorSystem: ActorSystem = SecurityApiTest.system

  val citizenUserId: UserId = UserId("citizen")
  val moderatorUserId: UserId = UserId("moderator")
  val adminUserId: UserId = UserId("admin")

  val routes: Route = sealRoute(securityApi.routes)

  Feature("create secure hash") {
    Scenario("user unauthorized") {
      Post("/admin/security/secure-hash") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("citizen forbidden") {
      Post("/admin/security/secure-hash")
        .withHeaders(Authorization(OAuth2BearerToken(tokenCitizen))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("moderator forbidden") {
      Post("/admin/security/secure-hash")
        .withHeaders(Authorization(OAuth2BearerToken(tokenModerator))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("admin with valid value") {
      Post("/admin/security/secure-hash")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "{\"value\": \"toto\"}"))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val secureHashResponse: SecureHashResponse = entityAs[SecureHashResponse]
        SecurityHelper.validateSecureHash(secureHashResponse.hash, "toto", securityConfiguration.secureHashSalt) shouldBe true
      }
    }

    Scenario("admin but no value given") {
      Post("/admin/security/secure-hash")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(tokenAdmin))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  Feature("validate secure hash") {
    Scenario("same hash value") {
      val hash = SecurityHelper.createSecureHash("toto", securityConfiguration.secureHashSalt)
      Post("/security/secure-hash")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"value": "toto", "hash": "$hash"}""")) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    Scenario("not the same hash value") {
      Post("/security/secure-hash")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"value": "toto", "hash": "ABCDE"}""")) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }

    }

    Scenario("missing argument") {
      Post("/security/secure-hash") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

}
object SecurityApiTest {
  val configuration: String =
    """
       |make-api {
       |  security {
       |    secure-hash-salt = "salt-secure"
       |    secure-vote-salt = "vote-secure"
       |  }
       |}
     """.stripMargin

  val system: ActorSystem = {
    val config = ConfigFactory.load(ConfigFactory.parseString(configuration))
    ActorSystem(classOf[SecurityApiTest].getSimpleName, config)
  }
}

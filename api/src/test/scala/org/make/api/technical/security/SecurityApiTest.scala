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

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.technical.auth.MakeDataHandlerComponent
import org.make.api.technical.{IdGeneratorComponent, MakeAuthenticationDirectives}
import org.make.api.{ActorSystemComponent, MakeApiTestBase}
import org.make.core.auth.UserRights
import org.make.core.user.{Role, UserId}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

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

  val citizenUserId = UserId("citizen")
  val moderatorUserId = UserId("moderator")
  val adminUserId = UserId("admin")

  val validAccessToken = "my-valid-access-token"
  val adminToken = "my-admin-access-token"
  val moderatorToken = "my-moderator-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)
  private val adminAccessToken = AccessToken(adminToken, None, None, Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken =
    AccessToken(moderatorToken, None, None, Some(1234567890L), tokenCreationDate)

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.eq(accessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(citizenUserId, Seq(Role.RoleCitizen), Seq.empty), None, Some("user"), None))
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.eq(moderatorAccessToken)))
    .thenReturn(
      Future
        .successful(Some(AuthInfo(UserRights(moderatorUserId, Seq(Role.RoleModerator), Seq.empty), None, None, None)))
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.eq(adminAccessToken)))
    .thenReturn(
      Future.successful(Some(AuthInfo(UserRights(adminUserId, Seq(Role.RoleAdmin), Seq.empty), None, None, None)))
    )

  val routes: Route = sealRoute(securityApi.routes)

  feature("create secure hash") {
    scenario("user unauthorized") {
      Post("/admin/security/secure-hash") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("citizen forbidden") {
      Post("/admin/security/secure-hash")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("moderator forbidden") {
      Post("/admin/security/secure-hash")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("admin with valid value") {
      Post("/admin/security/secure-hash")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "{\"value\": \"toto\"}"))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val secureHashResponse: SecureHashResponse = entityAs[SecureHashResponse]
        SecurityHelper.validateSecureHash(secureHashResponse.hash, "toto", securityConfiguration.secureHashSalt) shouldBe true
      }
    }

    scenario("admin but no value given") {
      Post("/admin/security/secure-hash")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  feature("validate secure hash") {
    scenario("same hash value") {
      Get(
        s"/security/secure-hash?hash=${SecurityHelper.createSecureHash("toto", securityConfiguration.secureHashSalt)}&value=toto"
      ) ~> routes ~> check {
        status should be(StatusCodes.NoContent)
      }
    }

    scenario("not the same hash value") {
      Get("/security/secure-hash?hash=ABCDE&value=toto") ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }

    }

    scenario("missing argument") {
      Get("/security/secure-hash") ~> routes ~> check {
        status should be(StatusCodes.NotFound)
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

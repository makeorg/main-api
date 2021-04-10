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

package org.make.api

import java.util.Date
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.sessionhistory.{SessionHistoryCoordinatorService, SessionHistoryCoordinatorServiceComponent}
import org.make.api.technical.MakeDirectives.MakeDirectivesDependencies
import org.make.api.technical._
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.security.{SecurityConfiguration, SecurityConfigurationComponent}
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.core.RequestContext
import org.make.core.auth.UserRights
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.tag.{TagId, TagTypeId}
import org.make.core.technical.IdGenerator
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator, RoleSuperAdmin}
import org.make.core.user.{User, UserId}
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future
import scala.concurrent.duration.Duration

trait MakeApiTestUtils extends MakeUnitTest with ScalatestRouteTest with MakeDirectives {
  this: MakeDirectivesDependencies =>

  def sealRoute(route: Route): Route =
    Route.seal(handleRejections(MakeApi.rejectionHandler)(route))
}

trait MakeApiTestBase
    extends MakeApiTestUtils
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with SecurityConfigurationComponent
    with MakeSettingsComponent
    with MakeDataHandlerComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent
    with EventBusServiceComponent
    with ActorSystemComponent {
  this: MakeAuthentication =>

  type MakeReadJournal = CassandraReadJournal

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService =
    mock[SessionHistoryCoordinatorService]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val proposalJournal: MakeReadJournal = mock[MakeReadJournal]
  override val userJournal: MakeReadJournal = mock[MakeReadJournal]
  override val sessionJournal: MakeReadJournal = mock[MakeReadJournal]
  override val securityConfiguration: SecurityConfiguration = mock[SecurityConfiguration]
  override val actorSystem: ActorSystem = ActorSystem()

  protected val secureCookieConfiguration: makeSettings.SecureCookie.type = mock[makeSettings.SecureCookie.type]
  when(makeSettings.SecureCookie).thenReturn(secureCookieConfiguration)
  when(secureCookieConfiguration.name).thenReturn("cookie-secure")
  when(secureCookieConfiguration.expirationName).thenReturn("cookie-secure-expiration")
  when(secureCookieConfiguration.isSecure).thenReturn(false)
  when(secureCookieConfiguration.domain).thenReturn(".foo.com")
  when(idGenerator.nextId()).thenReturn("some-id")

  protected val sessionCookieConfiguration: makeSettings.SessionCookie.type = mock[makeSettings.SessionCookie.type]
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.expirationName).thenReturn("cookie-session-expiration")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  when(sessionCookieConfiguration.domain).thenReturn(".foo.com")
  when(idGenerator.nextSessionId()).thenReturn(SessionId("some-id"))

  protected val visitorCookieConfiguration: makeSettings.VisitorCookie.type = mock[makeSettings.VisitorCookie.type]
  when(visitorCookieConfiguration.name).thenReturn("cookie-visitor")
  when(visitorCookieConfiguration.createdAtName).thenReturn("cookie-visitor-created-at")
  when(visitorCookieConfiguration.isSecure).thenReturn(false)
  when(visitorCookieConfiguration.domain).thenReturn(".foo.com")
  when(makeSettings.VisitorCookie).thenReturn(visitorCookieConfiguration)
  when(idGenerator.nextVisitorId()).thenReturn(VisitorId("some-id"))

  protected val userIdCookieConfiguration: makeSettings.UserIdCookie.type = mock[makeSettings.UserIdCookie.type]
  when(userIdCookieConfiguration.name).thenReturn("user-id-cookie")
  when(userIdCookieConfiguration.isSecure).thenReturn(false)
  when(userIdCookieConfiguration.domain).thenReturn(".foo.com")
  when(makeSettings.UserIdCookie).thenReturn(userIdCookieConfiguration)

  when(idGenerator.nextTagId()).thenReturn(TagId("some-id"))
  when(idGenerator.nextTagTypeId()).thenReturn(TagTypeId("some-id"))

  when(sessionHistoryCoordinatorService.convertSession(any[SessionId], any[UserId], any[RequestContext]))
    .thenReturn(Future.unit)

  when(oauth2DataHandler.refreshIfTokenIsExpired(any[String])).thenReturn(Future.successful(None))

  when(
    sessionHistoryCoordinatorService
      .getCurrentSessionId(any[SessionId], any[SessionId])
  ).thenAnswer { sessionId: SessionId =>
    Future.successful(sessionId)
  }

  //DEFINE Citizen, Moderator & Admin tokens and connections
  private val tokenCreationDate = new Date()

  final protected val tokenCitizen = "my-valid-citizen-access-token"
  final protected val tokenModerator = "my-valid-moderator-access-token"
  final protected val tokenAdmin = "my-valid-admin-access-token"
  final protected val tokenSuperAdmin = "my-valid-super-admin-access-token"

  final protected val defaultCitizenUser: User =
    TestUtils.user(id = UserId("my-citizen-user-id"), email = "yopmail+citizen@make.org", roles = Seq(RoleCitizen))
  final protected val defaultModeratorUser: User =
    TestUtils.user(id = UserId("my-moderator-user-id"), email = "mod.erator@modo.com", roles = Seq(RoleModerator))
  final protected val defaultAdminUser: User =
    TestUtils.user(id = UserId("my-admin-user-id"), email = "yopmail+admin@make.org", roles = Seq(RoleAdmin))
  final protected val defaultSuperAdminUser: User =
    TestUtils.user(
      id = UserId("my-super-admin-user-id"),
      email = "yopmail+super-admin@make.org",
      roles = Seq(RoleSuperAdmin)
    )

  private val defaultUsersByToken: Map[String, User] =
    Map(
      tokenCitizen -> defaultCitizenUser,
      tokenModerator -> defaultModeratorUser,
      tokenAdmin -> defaultAdminUser,
      tokenSuperAdmin -> defaultSuperAdminUser
    )

  // Override this Map to add more users by token if needed
  def customUserByToken: Map[String, User] = Map.empty

  private def usersByToken: Map[String, User] = defaultUsersByToken ++ customUserByToken

  when(oauth2DataHandler.findAccessToken(any[String])).thenAnswer { token: String =>
    val maybeAccessToken =
      usersByToken.get(token).map(_ => AccessToken(token, None, Some("user"), Some(1234567890L), tokenCreationDate))
    Future.successful(maybeAccessToken)
  }

  when(oauth2DataHandler.findAuthInfoByAccessToken(any[AccessToken]))
    .thenAnswer(
      (accessToken: AccessToken) =>
        Future.successful(
          usersByToken
            .get(accessToken.token)
            .map(
              user =>
                AuthInfo(
                  UserRights(
                    userId = user.userId,
                    roles = user.roles,
                    availableQuestions = user.availableQuestions,
                    emailVerified = user.emailVerified
                  ),
                  None,
                  None,
                  None
                )
            )
        )
    )

}

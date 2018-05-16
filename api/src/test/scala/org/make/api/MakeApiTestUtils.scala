package org.make.api

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.sessionhistory.{SessionHistoryCoordinatorService, SessionHistoryCoordinatorServiceComponent}
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.technical._
import org.make.api.technical.auth.{MakeAuthentication, MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.userhistory.{UserHistoryCoordinatorService, UserHistoryCoordinatorServiceComponent}
import org.make.core.session.{SessionId, VisitorId}
import org.make.core.user.UserId
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when

import scala.concurrent.Future
import scala.concurrent.duration.Duration

trait MakeApiTestUtils extends MakeUnitTest with ScalatestRouteTest with MakeDirectives {
  this: IdGeneratorComponent with MakeSettingsComponent with MakeAuthentication =>

  def sealRoute(route: Route): Route =
    Route.seal(handleRejections(MakeApi.rejectionHandler)(route))
}

trait MakeApiTestBase
    extends MakeApiTestUtils
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SessionHistoryCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent
    with EventBusServiceComponent
    with MakeSettingsComponent
    with ActorSystemComponent {
  this: MakeAuthentication =>

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val sessionHistoryCoordinatorService: SessionHistoryCoordinatorService =
    mock[SessionHistoryCoordinatorService]
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val readJournal: MakeReadJournal = mock[MakeReadJournal]
  override lazy val actorSystem: ActorSystem = ActorSystem()

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  when(idGenerator.nextId()).thenReturn("some-id")

  private val visitorCookieConfiguration = mock[makeSettings.VisitorCookie.type]
  when(visitorCookieConfiguration.name).thenReturn("cookie-visitor")
  when(visitorCookieConfiguration.isSecure).thenReturn(false)
  when(makeSettings.VisitorCookie).thenReturn(visitorCookieConfiguration)
  when(idGenerator.nextVisitorId()).thenReturn(VisitorId("some-id"))

  private val oauthConfiguration = mock[makeSettings.Oauth.type]
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)

  private val successful: Future[Unit] = Future.successful {}
  when(sessionHistoryCoordinatorService.convertSession(any[SessionId], any[UserId])).thenReturn(successful)
}

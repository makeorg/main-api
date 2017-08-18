package org.make.api.proposal

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestUtils
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.RequestContext
import org.make.core.proposal.ProposalId
import org.make.core.user.Role.RoleCitizen
import org.make.core.user.{User, UserId}
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers.{eq => matches, _}

import scala.concurrent.Future
import scalaoauth2.provider.{AccessToken, AuthInfo}

class ProposalApiTest
    extends MakeApiTestUtils
    with ProposalApi
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with ProposalServiceComponent {

  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val proposalService: ProposalService = mock[ProposalService]

  when(idGenerator.nextId()).thenReturn("next-id")

  val validAccessToken = "my-valid-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, Some("user"), Some(1234567890L), tokenCreationDate)
  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            User(
              userId = UserId("my-user-id"),
              email = "john.snow@night-watch.com",
              firstName = Some("John"),
              lastName = Some("Snoww"),
              lastIp = None,
              hashedPassword = None,
              enabled = true,
              verified = true,
              lastConnection = ZonedDateTime.now(ZoneOffset.UTC),
              verificationToken = None,
              verificationTokenExpiresAt = None,
              resetToken = None,
              resetTokenExpiresAt = None,
              roles = Seq(RoleCitizen),
              profile = None,
              createdAt = None,
              updatedAt = None
            ),
            None,
            Some("user"),
            None
          )
        )
      )
    )

  val validProposalText: String = "Il faut que tout le monde respecte les conventions de code"
  val invalidProposalText: String =
    "Il faut que le texte de la proposition n'exède pas une certaine limite, par exemple 140 caractères car sinon, " +
      "ça fait vraiment troooooop long. D'un autre côté on en dit peu en 140 caractères..."

  when(proposalService.propose(any[User], any[RequestContext], any[ZonedDateTime], matches(validProposalText)))
    .thenReturn(Future.successful(ProposalId("my-proposal-id")))

  val routes: Route = sealRoute(proposalRoutes)

  feature("proposing") {
    scenario("unauthenticated proposal") {
      Given("an un authenticated user")
      When("the user wants to propose")
      Then("he should get an unauthorized (401) return code")
      Post("/proposal").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated proposal") {
      Given("an authenticated user")
      When("the user wants to propose")
      Then("the proposal should be saved if valid")

      Post("/proposal")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"content": "$validProposalText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    scenario("invalid proposal") {
      Given("an authenticated user")
      When("the user wants to propose")
      Then("the proposal should be rejected if invalid")

      Post("/proposal")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"""{"content": "$invalidProposalText"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }
}

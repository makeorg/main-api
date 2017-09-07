package org.make.api.proposal

import java.time.ZonedDateTime
import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import io.circe.syntax._
import org.make.api.MakeApiTestUtils
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.{Proposal, ProposalId}
import org.make.core.reference.{LabelId, TagId, ThemeId}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationFailedError}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito._

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
  val adminToken = "my-admin-access-token"
  val moderatorToken = "my-moderator-access-token"
  val tokenCreationDate = new Date()
  private val accessToken = AccessToken(validAccessToken, None, None, Some(1234567890L), tokenCreationDate)
  private val adminAccessToken = AccessToken(adminToken, None, None, Some(1234567890L), tokenCreationDate)
  private val moderatorAccessToken =
    AccessToken(moderatorToken, None, None, Some(1234567890L), tokenCreationDate)

  val validateProposalEntity: String = ValidateProposalRequest(
    newContent = None,
    sendNotificationEmail = true,
    theme = Some(ThemeId("fire and ice")),
    labels = Seq(LabelId("sex"), LabelId("violence")),
    tags = Seq(TagId("dragon"), TagId("sword")),
    similarProposals = Seq()
  ).asJson.toString

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))

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
              lastConnection = DateHelper.now(),
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

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(
          AuthInfo(
            User(
              userId = UserId("the-mother-of-dragons"),
              email = "d.narys@tergarian.com",
              firstName = Some("Daenerys"),
              lastName = Some("Tergarian"),
              lastIp = None,
              hashedPassword = None,
              enabled = true,
              verified = true,
              lastConnection = DateHelper.now(),
              verificationToken = None,
              verificationTokenExpiresAt = None,
              resetToken = None,
              resetTokenExpiresAt = None,
              roles = Seq(RoleAdmin),
              profile = None,
              createdAt = None,
              updatedAt = None
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
      Future.successful(
        Some(
          AuthInfo(
            User(
              userId = UserId("the-dwarf"),
              email = "tyrion@pays-his-debts.com",
              firstName = Some("Tyrion"),
              lastName = Some("Lannister"),
              lastIp = None,
              hashedPassword = None,
              enabled = true,
              verified = true,
              lastConnection = DateHelper.now(),
              verificationToken = None,
              verificationTokenExpiresAt = None,
              resetToken = None,
              resetTokenExpiresAt = None,
              roles = Seq(RoleModerator),
              profile = None,
              createdAt = None,
              updatedAt = None
            ),
            None,
            None,
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

  when(
    proposalService
      .validateProposal(matches(ProposalId("123456")), any[UserId], any[RequestContext], any[ValidateProposalRequest])
  ).thenReturn(Future.successful(proposal(ProposalId("123456"))))

  when(
    proposalService
      .validateProposal(matches(ProposalId("987654")), any[UserId], any[RequestContext], any[ValidateProposalRequest])
  ).thenReturn(Future.successful(proposal(ProposalId("987654"))))

  when(
    proposalService
      .validateProposal(matches(ProposalId("nop")), any[UserId], any[RequestContext], any[ValidateProposalRequest])
  ).thenReturn(Future.failed(ValidationFailedError(Seq())))

  private def proposal(id: ProposalId): Proposal = {
    Proposal(
      proposalId = id,
      slug = "a-song-of-fire-and-ice",
      content = "A song of fire and ice",
      author = UserId("Georges RR Martin"),
      labels = Seq(),
      theme = None,
      status = Accepted,
      tags = Seq(),
      creationContext = RequestContext.empty,
      createdAt = Some(DateHelper.now()),
      updatedAt = Some(DateHelper.now()),
      events = Nil
    )
  }

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

  feature("proposal validation") {
    scenario("unauthenticated validation") {
      Post("/proposal/123456/accept") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }
    scenario("validation with user role") {
      Post("/proposal/123456/accept")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }
    scenario("validation with moderation role") {

      Post("/proposal/123456/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
    scenario("validation with admin role") {
      Post("/proposal/987654/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
    scenario("validation of non existing with admin role") {
      Post("/proposal/nop/accept")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validateProposalEntity))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }
}

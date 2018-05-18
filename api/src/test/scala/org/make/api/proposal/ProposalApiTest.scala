package org.make.api.proposal

import java.time.ZonedDateTime
import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken, RawHeader}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import io.circe.syntax._
import org.make.api.MakeApiTestBase
import org.make.api.idea.{IdeaService, IdeaServiceComponent}
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.api.user.{UserResponse, UserService, UserServiceComponent}
import org.make.core.auth.UserRights
import org.make.core.idea.{Idea, IdeaId}
import org.make.core.operation.{Operation, OperationId, OperationStatus}
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal.indexed._
import org.make.core.proposal.{ProposalId, ProposalStatus, SearchQuery, _}
import org.make.core.reference._
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError, ValidationFailedError}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito._
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.concurrent.Future

class ProposalApiTest
    extends MakeApiTestBase
    with ProposalApi
    with IdeaServiceComponent
    with ProposalServiceComponent
    with UserServiceComponent
    with ThemeServiceComponent
    with OperationServiceComponent {

  override val proposalService: ProposalService = mock[ProposalService]

  override val userService: UserService = mock[UserService]
  override val themeService: ThemeService = mock[ThemeService]
  override val ideaService: IdeaService = mock[IdeaService]
  override val operationService: OperationService = mock[OperationService]

  private val john = User(
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
    country = "FR",
    language = "fr",
    profile = None,
    createdAt = None,
    updatedAt = None,
    isHardBounce = false,
    lastMailingError = None
  )

  val daenerys = User(
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
    country = "FR",
    language = "fr",
    profile = None,
    createdAt = None,
    updatedAt = None
  )

  val tyrion = User(
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
    country = "FR",
    language = "fr",
    profile = None,
    createdAt = None,
    updatedAt = None
  )

  when(userService.getUser(any[UserId])).thenReturn(Future.successful(Some(john)))

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
    similarProposals = Seq(),
    idea = Some(IdeaId("becoming-king")),
    operation = None
  ).asJson.toString

  val refuseProposalWithReasonEntity: String =
    RefuseProposalRequest(sendNotificationEmail = true, refusalReason = Some("not allowed word")).asJson.toString

  when(oauth2DataHandler.findAccessToken(validAccessToken)).thenReturn(Future.successful(Some(accessToken)))
  when(oauth2DataHandler.findAccessToken(adminToken)).thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAccessToken(moderatorToken)).thenReturn(Future.successful(Some(moderatorAccessToken)))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(Future.successful(Some(AuthInfo(UserRights(john.userId, john.roles), None, Some("user"), None))))

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(Some(AuthInfo(UserRights(userId = daenerys.userId, roles = daenerys.roles), None, None, None)))
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(Future.successful(Some(AuthInfo(UserRights(tyrion.userId, tyrion.roles), None, None, None))))

  val validProposalText: String = "Il faut que tout le monde respecte les conventions de code"
  val invalidMaxLengthProposalText: String =
    "Il faut que le texte de la proposition n'exède pas une certaine limite, par exemple 140 caractères car sinon, " +
      "ça fait vraiment troooooop long. D'un autre côté on en dit peu en 140 caractères..."

  val invalidMinLengthProposalText: String = "Il faut"

  when(
    proposalService
      .propose(
        any[User],
        any[RequestContext],
        any[ZonedDateTime],
        matches(validProposalText),
        any[Option[OperationId]],
        any[Option[ThemeId]],
        any[Option[String]],
        any[Option[String]]
      )
  ).thenReturn(Future.successful(ProposalId("my-proposal-id")))

  when(
    proposalService
      .validateProposal(matches(ProposalId("123456")), any[UserId], any[RequestContext], any[ValidateProposalRequest])
  ).thenReturn(Future.successful(Some(proposal(ProposalId("123456")))))

  when(
    proposalService
      .validateProposal(matches(ProposalId("987654")), any[UserId], any[RequestContext], any[ValidateProposalRequest])
  ).thenReturn(Future.successful(Some(proposal(ProposalId("987654")))))

  when(
    proposalService
      .validateProposal(matches(ProposalId("nop")), any[UserId], any[RequestContext], any[ValidateProposalRequest])
  ).thenReturn(Future.failed(ValidationFailedError(Seq())))

  when(
    proposalService
      .refuseProposal(matches(ProposalId("123456")), any[UserId], any[RequestContext], any[RefuseProposalRequest])
  ).thenReturn(Future.successful(Some(proposal(ProposalId("123456")))))

  when(
    proposalService
      .refuseProposal(matches(ProposalId("987654")), any[UserId], any[RequestContext], any[RefuseProposalRequest])
  ).thenReturn(Future.successful(Some(proposal(ProposalId("987654")))))

  when(
    proposalService
      .lockProposal(matches(ProposalId("123456")), any[UserId], any[RequestContext])
  ).thenReturn(Future.failed(ValidationFailedError(Seq(ValidationError("moderatorName", Some("mauderator"))))))

  when(
    proposalService
      .lockProposal(matches(ProposalId("123456")), matches(tyrion.userId), any[RequestContext])
  ).thenReturn(Future.successful(Some(tyrion.userId)))

  when(
    proposalService
      .getModerationProposalById(matches(ProposalId("sim-123")))
  ).thenReturn(
    Future.successful(
      Some(
        ProposalResponse(
          proposalId = ProposalId("sim-123"),
          slug = "a-song-of-fire-and-ice",
          content = "A song of fire and ice",
          author = UserResponse(
            UserId("Georges RR Martin"),
            email = "g@rr.martin",
            firstName = Some("Georges"),
            lastName = Some("Martin"),
            enabled = true,
            verified = true,
            lastConnection = DateHelper.now(),
            roles = Seq.empty,
            None,
            country = "FR",
            language = "fr",
            isHardBounce = false,
            lastMailingError = None
          ),
          labels = Seq(),
          theme = None,
          status = Accepted,
          tags = Seq(),
          votes = Seq(
            Vote(key = VoteKey.Agree, qualifications = Seq.empty),
            Vote(key = VoteKey.Disagree, qualifications = Seq.empty),
            Vote(key = VoteKey.Neutral, qualifications = Seq.empty)
          ),
          context = RequestContext.empty,
          createdAt = Some(DateHelper.now()),
          updatedAt = Some(DateHelper.now()),
          events = Nil,
          similarProposals = Seq(ProposalId("sim-456"), ProposalId("sim-789")),
          idea = None,
          ideaProposals = Seq.empty,
          operationId = None,
          language = Some("fr"),
          country = Some("FR")
        )
      )
    )
  )

  val proposalResult: ProposalResult = ProposalResult(
    id = ProposalId("aaa-bbb-ccc"),
    userId = UserId("foo-bar"),
    content = "il faut fou",
    slug = "il-faut-fou",
    status = ProposalStatus.Accepted,
    createdAt = DateHelper.now(),
    updatedAt = None,
    votes = Seq.empty,
    context = None,
    trending = None,
    labels = Seq.empty,
    author = Author(None, None, None),
    country = "TN",
    language = "ar",
    themeId = None,
    tags = Seq.empty,
    myProposal = false,
    idea = None,
    operationId = None
  )
  when(
    proposalService
      .searchForUser(any[Option[UserId]], any[SearchQuery], any[Option[Int]], any[RequestContext])
  ).thenReturn(Future.successful(ProposalsResultSeededResponse(1, Seq(proposalResult), Some(42))))

  private def proposal(id: ProposalId): ProposalResponse = {
    ProposalResponse(
      proposalId = id,
      slug = "a-song-of-fire-and-ice",
      content = "A song of fire and ice",
      author = UserResponse(
        UserId("Georges RR Martin"),
        email = "g@rr.martin",
        firstName = Some("Georges"),
        lastName = Some("Martin"),
        enabled = true,
        verified = true,
        lastConnection = DateHelper.now(),
        roles = Seq.empty,
        None,
        country = "FR",
        language = "fr",
        isHardBounce = false,
        lastMailingError = None
      ),
      labels = Seq(),
      theme = None,
      status = Accepted,
      tags = Seq(),
      votes = Seq(
        Vote(key = VoteKey.Agree, qualifications = Seq.empty),
        Vote(key = VoteKey.Disagree, qualifications = Seq.empty),
        Vote(key = VoteKey.Neutral, qualifications = Seq.empty)
      ),
      context = RequestContext.empty,
      createdAt = Some(DateHelper.now()),
      updatedAt = Some(DateHelper.now()),
      events = Nil,
      similarProposals = Seq.empty,
      idea = None,
      ideaProposals = Seq.empty,
      operationId = None,
      language = Some("fr"),
      country = Some("FR")
    )
  }

  when(ideaService.fetchOne(any[IdeaId]))
    .thenReturn(
      Future.successful(
        Some(
          Idea(
            ideaId = IdeaId("foo"),
            name = "Foo",
            createdAt = Some(DateHelper.now()),
            updatedAt = Some(DateHelper.now())
          )
        )
      )
    )

  val routes: Route = sealRoute(proposalRoutes)

  when(operationService.findOne(matches(OperationId("1234-1234")))).thenReturn(
    Future.successful(
      Some(
        Operation(
          status = OperationStatus.Pending,
          operationId = OperationId("1234-1234"),
          slug = "my-operation",
          translations = Seq.empty,
          defaultLanguage = "FR",
          events = List.empty,
          createdAt = None,
          updatedAt = None,
          countriesConfiguration = Seq.empty
        )
      )
    )
  )
  when(operationService.findOne(matches(OperationId("fake")))).thenReturn(Future.successful(None))

  feature("proposing") {
    scenario("unauthenticated proposal") {
      Given("an un authenticated user")
      When("the user wants to propose")
      Then("he should get an unauthorized (401) return code")
      Post("/proposals").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated proposal") {
      Given("an authenticated user")
      When("the user wants to propose")
      Then("the proposal should be saved if valid")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    scenario("invalid proposal due to max length") {
      Given("an authenticated user")
      When("the user wants to propose a long proposal")
      Then("the proposal should be rejected if invalid")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$invalidMaxLengthProposalText", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "content")
        contentError should be(Some(ValidationError("content", Some("content should not be longer than 140"))))
      }
    }

    scenario("invalid proposal due to min length") {
      Given("an authenticated user")
      When("the user wants to propose a short proposal")
      Then("the proposal should be rejected if invalid")

      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$invalidMinLengthProposalText", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "content")
        contentError should be(Some(ValidationError("content", Some("content should not be shorter than 12"))))
      }
    }

    scenario("invalid proposal due to bad operation") {
      Given("an authenticated user")
      And("a bad operationId")
      When("the user want to propose in an operation context")
      Then("the proposal should be rejected")
      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "operationId": "fake", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken)), RawHeader("x-make-operation", "1234-1234")) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "operationId")
        contentError should be(Some(ValidationError("operationId", Some("Invalid operationId"))))
      }
    }

    scenario("invalid proposal without language") {
      Given("an authenticated user")
      And("an empty language")
      When("the user want to propose without language")
      Then("the proposal should be rejected")
      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "operationId": "fake", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "language")
        contentError should be(Some(ValidationError("language", Some("language is mandatory"))))
      }
    }

    scenario("invalid proposal without country") {
      Given("an authenticated user")
      And("an empty country")
      When("the user want to propose without country")
      Then("the proposal should be rejected")
      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "operationId": "fake", "language": "fr"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "country")
        contentError should be(Some(ValidationError("country", Some("country is mandatory"))))
      }
    }

    scenario("valid proposal with operation, language and country") {
      Given("an authenticated user")
      And("a valid operationId")
      When("the user want to propose in an operation context")
      Then("the proposal should be saved")
      Post("/proposals")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            s"""{"content": "$validProposalText", "operationId": "1234-1234", "language": "fr", "country": "FR"}"""
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken)), RawHeader("x-make-operation", "fake")) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }
  }

  feature("search proposal from front") {
    scenario("search and get results + total") {
      Post("/proposals/search")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{
                  | "content": "faut",
                  | "limit": 10,
                  | "skip": 0
                  |}""".stripMargin)) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val proposalResults: ProposalsResultResponse = entityAs[ProposalsResultResponse]
        proposalResults.total should be(1)
        proposalResults.results should be(Seq(proposalResult))
      }
    }
  }
}

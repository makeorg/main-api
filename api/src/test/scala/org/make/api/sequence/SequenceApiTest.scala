package org.make.api.sequence

import java.time.ZonedDateTime
import java.util.Date

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import io.circe.generic.auto._
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.api.theme.{ThemeService, ThemeServiceComponent}
import org.make.core.auth.UserRights
import org.make.core.proposal.ProposalId
import org.make.core.reference.{Tag, TagId, Theme, ThemeId}
import org.make.core.sequence.indexed.SequencesSearchResult
import org.make.core.sequence.{SearchQuery, Sequence, SequenceId, SequenceStatus}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.UserId
import org.make.core.{DateHelper, RequestContext, ValidationError}
import org.mockito.ArgumentMatchers.{eq => matches, _}
import org.mockito.Mockito._

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scalaoauth2.provider.{AccessToken, AuthInfo}

class SequenceApiTest
    extends MakeApiTestUtils
    with SequenceApi
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with SequenceServiceComponent
    with MakeSettingsComponent
    with ThemeServiceComponent
    with TagServiceComponent {

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val sequenceService: SequenceService = mock[SequenceService]
  override val themeService: ThemeService = mock[ThemeService]
  override val tagService: TagService = mock[TagService]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]
  val CREATED_DATE_SECOND_MINUS: Int = 10
  val mainCreatedAt: Option[ZonedDateTime] = Some(DateHelper.now().minusSeconds(CREATED_DATE_SECOND_MINUS))
  val mainUpdatedAt: Option[ZonedDateTime] = Some(DateHelper.now())

  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(idGenerator.nextId()).thenReturn("next-id")
  when(themeService.findAll()).thenReturn(
    Future.successful(
      Seq(
        Theme(
          themeId = ThemeId("123"),
          translations = Seq.empty,
          actionsCount = 0,
          proposalsCount = 0,
          country = "FR",
          color = "#123123",
          gradient = None,
          tags = Seq.empty
        )
      )
    )
  )
  when(themeService.findByIds(matches(Seq(ThemeId("123"))))).thenReturn(
    Future.successful(
      Seq(
        Theme(
          themeId = ThemeId("123"),
          translations = Seq.empty,
          actionsCount = 0,
          proposalsCount = 0,
          country = "FR",
          color = "#123123",
          gradient = None,
          tags = Seq.empty
        )
      )
    )
  )
  when(themeService.findByIds(matches(Seq.empty))).thenReturn(Future.successful(Seq.empty))
  when(themeService.findByIds(matches(Seq(ThemeId("badthemeid"))))).thenReturn(Future.successful(Seq.empty))

  when(tagService.findAllEnabled()).thenReturn(Future.successful(Seq(Tag("mytag"))))
  when(tagService.findByTagIds(matches(Seq(TagId("mytag"))))).thenReturn(Future.successful(Seq(Tag("mytag"))))
  when(tagService.findByTagIds(matches(Seq.empty))).thenReturn(Future.successful(Seq.empty))
  when(tagService.findByTagIds(matches(Seq(TagId("badtagid"))))).thenReturn(Future.successful(Seq.empty))

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

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(accessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(userId = UserId("my-user-id"), roles = Seq(RoleCitizen)), None, Some("user"), None))
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(adminAccessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(userId = UserId("the-mother-of-dragons"), roles = Seq(RoleAdmin)), None, None, None))
      )
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(matches(moderatorAccessToken)))
    .thenReturn(
      Future.successful(
        Some(AuthInfo(UserRights(userId = UserId("the-dwarf"), roles = Seq(RoleModerator)), None, None, None))
      )
    )

  val defaultSequence = Sequence(
    sequenceId = SequenceId("123"),
    title = "my sequence 1",
    slug = "my-sequence-1",
    tagIds = Seq.empty,
    proposalIds = Seq.empty,
    themeIds = Seq.empty,
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    status = SequenceStatus.Published,
    creationContext = RequestContext.empty,
    sequenceTranslation = Seq.empty,
    events = Nil,
    searchable = false
  )
  private def sequenceResponse(id: SequenceId): SequenceResponse = {
    SequenceResponse(
      sequenceId = id,
      slug = "my-sequence-1",
      title = "my sequence 1",
      status = SequenceStatus.Published,
      tagIds = Seq.empty,
      themeIds = Seq.empty,
      creationContext = RequestContext.empty,
      createdAt = Some(DateHelper.now()),
      updatedAt = Some(DateHelper.now()),
      events = Nil
    )
  }

  val sequenceModeratorSearchResult = SequencesSearchResult(0, Seq.empty)

  val validCreateJson: String =
    """
      |{
      | "tagIds": ["happy"],
      | "themeIds": [],
      | "title": "my valid sequence",
      | "searchable": true
      |}
    """.stripMargin

  val invalidCreateJson: String =
    """
      |{
      | "tagIds": ["happy"],
      | "themeIds": ["909090"],
      | "title": "my valid sequence",
      | "searchable": true
      |}
    """.stripMargin

  val validModeratorSearchJson: String =
    """
      |{
      | "tagIds": [],
      | "themeIds": [],
      | "title": "my sequence 1",
      | "slug": "my-sequence-1",
      | "sorts": []
      |}
    """.stripMargin

  val routes: Route = sealRoute(sequenceRoutes)

  when(
    sequenceService
      .create(
        any[UserId],
        any[RequestContext],
        any[ZonedDateTime],
        any[String],
        any[Seq[TagId]],
        any[Seq[ThemeId]],
        matches(true)
      )
  ).thenReturn(Future.successful(Some(sequenceResponse(SequenceId("43")))))

  when(
    sequenceService
      .addProposals(
        matches(SequenceId("add1")),
        any[UserId],
        any[RequestContext],
        matches(Seq(ProposalId("proposal1")))
      )
  ).thenReturn(Future.successful(Some(sequenceResponse(SequenceId("default")))))

  when(
    sequenceService
      .addProposals(
        matches(SequenceId("notexistsequenceId")),
        any[UserId],
        any[RequestContext],
        matches(Seq(ProposalId("proposal1")))
      )
  ).thenReturn(Future.successful(None))

  when(
    sequenceService
      .removeProposals(
        matches(SequenceId("remove1")),
        any[UserId],
        any[RequestContext],
        matches(Seq(ProposalId("proposal1")))
      )
  ).thenReturn(Future.successful(Some(sequenceResponse(SequenceId("default")))))

  when(
    sequenceService
      .removeProposals(
        matches(SequenceId("notexistsequenceId")),
        any[UserId],
        any[RequestContext],
        matches(Seq(ProposalId("proposal1")))
      )
  ).thenReturn(Future.successful(None))

  when(
    sequenceService
      .update(
        matches(SequenceId("moderationSequence1")),
        any[UserId],
        any[RequestContext],
        matches(Some("newSequenceTitle")),
        matches(None),
        matches(None),
        matches(Seq.empty),
        matches(Seq.empty)
      )
  ).thenReturn(Future.successful(Some(sequenceResponse(SequenceId("default")))))

  when(
    sequenceService
      .update(
        matches(SequenceId("notexistsequenceId")),
        any[UserId],
        any[RequestContext],
        matches(Some("newSequenceTitle")),
        matches(None),
        matches(None),
        matches(Seq.empty),
        matches(Seq.empty)
      )
  ).thenReturn(Future.successful(None))

  when(sequenceService.getSequenceById(any[SequenceId], any[RequestContext]))
    .thenReturn(Future.successful(Some(defaultSequence)))

  when(sequenceService.getSequenceById(matches(SequenceId("notexistsequenceId")), any[RequestContext]))
    .thenReturn(Future.successful(None))

  when(sequenceService.search(any[Option[UserId]], any[SearchQuery], any[RequestContext]))
    .thenReturn(Future.successful(sequenceModeratorSearchResult))

  when(
    sequenceService
      .startNewSequence(any[Option[UserId]], matches("start-sequence"), any[Seq[ProposalId]], any[RequestContext])
  ).thenReturn(
    Future.successful(
      Some(
        SequenceResult(
          id = SequenceId("searchSequence"),
          title = "sequence search",
          slug = "start-sequence",
          proposals = Seq.empty
        )
      )
    )
  )

  feature("creating") {
    scenario("unauthenticated user") {
      Given("an un authenticated user")
      When("the user wants to create a sequence")
      Then("he should get an unauthorized (401) return code")
      Post("/moderation/sequences").withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated user with citizen role") {
      Given("an authenticated user with citizen role")
      When("the user wants to create a sequence")
      Then("he should get an forbidden (403) return code")

      Post("/moderation/sequences")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validCreateJson))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("authenticated user with moderator role") {
      Given("an authenticated user with moderator role")
      When("the user wants to create a sequence")
      Then("he should get an success (201) return code")

      Post("/moderation/sequences")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validCreateJson))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    scenario("create sequence without themes and tags field") {
      Given("an authenticated user with moderator role")
      When("the user wants to create a sequence without tags or themes field")
      Then("he should get a bad request (400) return code")

      Post("/moderation/sequences")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"title": "my valid sequence"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("create sequence without title field") {
      Given("an authenticated user with moderator role")
      When("the user wants to create a sequence without title field")
      Then("he should get a bad request (400) return code")

      Post("/moderation/sequences")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"themeIds: [], tagIds: []"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("invalid sequence due to bad theme") {
      Given("an authenticated moderator")
      When("the moderator wants to create a sequence with an invalid theme")
      Then("the sequence should be rejected if invalid")

      Post("/moderation/sequences")
        .withEntity(HttpEntity(ContentTypes.`application/json`, invalidCreateJson))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "themeIds")
        contentError should be(Some(ValidationError("themeIds", Some("Some theme ids are invalid"))))
      }
    }
  }

  feature("sequence add proposals") {
    scenario("unauthenticated user") {
      Post("/moderation/sequences/add1/proposals/add") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated user with citizen role") {
      Post("/moderation/sequences/add1/proposals/add")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("invalid request") {
      Post("/moderation/sequences/add1/proposals/add")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("valid request with a sequence id that not exist") {
      Post("/moderation/sequences/notexistsequenceId/proposals/add")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["proposal1"]}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("valid request") {
      Post("/moderation/sequences/add1/proposals/add")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["proposal1"]}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  feature("sequence remove proposals") {
    scenario("unauthenticated user") {
      Post("/moderation/sequences/remove1/proposals/remove") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated user with citizen role") {
      Post("/moderation/sequences/remove1/proposals/remove")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("invalid request") {
      Post("/moderation/sequences/remove1/proposals/remove")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("valid request with a sequence id that not exist") {
      Post("/moderation/sequences/notexistsequenceId/proposals/remove")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["proposal1"]}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("valid request") {
      Post("/moderation/sequences/remove1/proposals/remove")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"proposalIds": ["proposal1"]}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  feature("sequence moderation search") {
    scenario("unauthenticated user") {
      Post("/moderation/sequences/search") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated user with citizen role") {
      Post("/moderation/sequences/search")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("invalid request") {
      Post("/moderation/sequences/search")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("valid request") {
      Post("/moderation/sequences/search")
        .withEntity(HttpEntity(ContentTypes.`application/json`, validModeratorSearchJson))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  feature("sequence start by slug") {
    scenario("unauthenticated user") {
      Post("/moderation/sequences/search") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("valid request") {
      Get("/sequences/start-sequence")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  feature("sequence update proposal") {
    scenario("unauthenticated user") {
      Patch("/moderation/sequences/moderationSequence1") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("authenticated user with citizen role") {
      Patch("/moderation/sequences/moderationSequence1")
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("invalid request") {
      Patch("/moderation/sequences/moderationSequence1")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    scenario("invalid status") {
      Patch("/moderation/sequences/moderationSequence1")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"status": "badstatus"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "status")
        contentError should be(Some(ValidationError("status", Some("Invalid status"))))
      }
    }

    scenario("invalid themeId") {
      Patch("/moderation/sequences/moderationSequence1")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"themeIds": ["badthemeid"]}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "themeIds")
        contentError should be(Some(ValidationError("themeIds", Some("Some theme ids are invalid"))))
      }
    }

    scenario("invalid tagId") {
      Patch("/moderation/sequences/moderationSequence1")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"tagIds": ["badtagid"]}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "tagIds")
        contentError should be(Some(ValidationError("tagIds", Some("Some tag ids are invalid"))))
      }
    }

    scenario("valid request with a sequence id that not exist") {
      Patch("/moderation/sequences/notexistsequenceId")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"title": "newSequenceTitle"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("valid request") {
      Patch("/moderation/sequences/moderationSequence1")
        .withEntity(HttpEntity(ContentTypes.`application/json`, """{"title": "newSequenceTitle"}"""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }
}

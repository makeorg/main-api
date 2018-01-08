package org.make.api.operation

import java.time.ZonedDateTime
import java.util.{Date, UUID}

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import org.make.api.MakeApiTestUtils
import org.make.api.extensions.{MakeSettings, MakeSettingsComponent}
import org.make.api.sequence.{SequenceResponse, SequenceService, SequenceServiceComponent}
import org.make.api.tag.{TagService, TagServiceComponent}
import org.make.api.technical.auth.{MakeDataHandler, MakeDataHandlerComponent}
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.api.user.{UserResponse, UserService, UserServiceComponent}
import org.make.core.auth.UserRights
import org.make.core.operation._
import org.make.core.reference.{Tag, TagId}
import org.make.core.sequence.{SequenceId, SequenceStatus}
import org.make.core.user.Role.{RoleAdmin, RoleCitizen, RoleModerator}
import org.make.core.user.{User, UserId}
import org.make.core.{DateHelper, RequestContext, ValidationError}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scalaoauth2.provider.{AccessToken, AuthInfo}

class ModerationOperationApiTest
    extends MakeApiTestUtils
    with ModerationOperationApi
    with TagServiceComponent
    with SequenceServiceComponent
    with IdGeneratorComponent
    with MakeDataHandlerComponent
    with OperationServiceComponent
    with MakeSettingsComponent
    with UserServiceComponent {

  override val makeSettings: MakeSettings = mock[MakeSettings]
  override val idGenerator: IdGenerator = mock[IdGenerator]
  override val oauth2DataHandler: MakeDataHandler = mock[MakeDataHandler]
  override val operationService: OperationService = mock[OperationService]
  override val tagService: TagService = mock[TagService]
  override val sequenceService: SequenceService = mock[SequenceService]
  override val userService: UserService = mock[UserService]

  private val sessionCookieConfiguration = mock[makeSettings.SessionCookie.type]
  private val oauthConfiguration = mock[makeSettings.Oauth.type]

  val routes: Route = sealRoute(moderationOperationRoutes)
  val userId: UserId = UserId(UUID.randomUUID().toString)
  val now: ZonedDateTime = DateHelper.now()

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
    profile = None,
    createdAt = None,
    updatedAt = None
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
    profile = None,
    createdAt = None,
    updatedAt = None
  )

  val firstOperation: Operation = Operation(
    status = OperationStatus.Pending,
    operationId = OperationId("firstOperation"),
    slug = "first-operation",
    translations = Seq(
      OperationTranslation(title = "première operation", language = "fr"),
      OperationTranslation(title = "first operation", language = "en")
    ),
    defaultLanguage = "fr",
    sequenceLandingId = SequenceId("first-sequence-id"),
    events = List(
      OperationAction(
        date = now,
        makeUserId = john.userId,
        actionType = OperationCreateAction.name,
        arguments = Map("arg1" -> "valueArg1")
      )
    ),
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    countriesConfiguration = Seq(OperationCountryConfiguration(countryCode = "BR", tagIds = Seq.empty))
  )

  val secondOperation: Operation = Operation(
    status = OperationStatus.Pending,
    operationId = OperationId("secondOperation"),
    slug = "second-operation",
    translations = Seq(
      OperationTranslation(title = "secondo operazione", language = "it"),
      OperationTranslation(title = "second operation", language = "en")
    ),
    defaultLanguage = "it",
    sequenceLandingId = SequenceId("second-sequence-id"),
    events = List(
      OperationAction(
        date = now,
        makeUserId = john.userId,
        actionType = OperationCreateAction.name,
        arguments = Map("arg1" -> "valueArg1")
      )
    ),
    createdAt = Some(DateHelper.now()),
    updatedAt = Some(DateHelper.now()),
    countriesConfiguration = Seq(OperationCountryConfiguration(countryCode = "IT", tagIds = Seq.empty))
  )

  val validCreateJson: String =
    """
      |{
      |  "slug": "my-create-operation",
      |  "translations": [
      |    {
      |      "title": "first create operation",
      |      "language": "fr"
      |    }
      |  ],
      |  "defaultLanguage": "fr",
      |  "sequenceLandingId": "29625b5a-56da-4539-b195-15303187c20b",
      |  "countriesConfiguration": [
      |    {
      |      "countryCode": "FR",
      |      "tagIds": [
      |        "hello"
      |      ]
      |    }
      |  ]
      |}
    """.stripMargin

  val validUpdateJson: String =
    """
      |{
      |  "status": "Active",
      |  "slug": "my-update-operation",
      |  "translations": [
      |    {
      |      "title": "first update operation",
      |      "language": "fr"
      |    }
      |  ],
      |  "defaultLanguage": "fr",
      |  "sequenceLandingId": "29625b5a-56da-4539-b195-15303187c20b",
      |  "countriesConfiguration": [
      |    {
      |      "countryCode": "FR",
      |      "tagIds": [
      |        "hello"
      |      ]
      |    }
      |  ]
      |}
    """.stripMargin

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
    .thenReturn(Future.successful(Some(AuthInfo(UserRights(john.userId, john.roles), None, Some("user"), None))))

  when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.eq(adminAccessToken)))
    .thenReturn(
      Future.successful(Some(AuthInfo(UserRights(userId = daenerys.userId, roles = daenerys.roles), None, None, None)))
    )

  when(oauth2DataHandler.findAuthInfoByAccessToken(ArgumentMatchers.eq(moderatorAccessToken)))
    .thenReturn(Future.successful(Some(AuthInfo(UserRights(tyrion.userId, tyrion.roles), None, None, None))))

  when(userService.getUser(any[UserId])).thenReturn(Future.successful(Some(john)))
  when(sessionCookieConfiguration.name).thenReturn("cookie-session")
  when(sessionCookieConfiguration.isSecure).thenReturn(false)
  when(sessionCookieConfiguration.lifetime).thenReturn(Duration("20 minutes"))
  when(makeSettings.SessionCookie).thenReturn(sessionCookieConfiguration)
  when(makeSettings.Oauth).thenReturn(oauthConfiguration)
  when(idGenerator.nextId()).thenReturn("next-id")

  when(operationService.findOne(OperationId("firstOperation"))).thenReturn(Future.successful(Some(firstOperation)))
  when(operationService.findOne(OperationId("fakeid"))).thenReturn(Future.successful(None))
  when(operationService.find()).thenReturn(Future.successful(Seq(firstOperation, secondOperation)))
  when(tagService.findByTagIds(Seq(TagId("hello")))).thenReturn(Future.successful(Seq(Tag("hello"))))
  when(tagService.findByTagIds(Seq(TagId("fakeTag")))).thenReturn(Future.successful(Seq()))
  when(sequenceService.getModerationSequenceById(SequenceId("29625b5a-56da-4539-b195-15303187c20b"))).thenReturn(
    Future.successful(
      Option(
        SequenceResponse(
          sequenceId = SequenceId("29625b5a-56da-4539-b195-15303187c20b"),
          title = "sequence",
          slug = "slug",
          themeIds = Seq.empty,
          createdAt = None,
          updatedAt = None,
          creationContext = RequestContext.empty,
          events = List.empty,
          status = SequenceStatus.Published
        )
      )
    )
  )
  when(sequenceService.getModerationSequenceById(SequenceId("fakeSequenceId"))).thenReturn(Future.successful(None))

  when(operationService.findOneBySlug("my-create-operation")).thenReturn(Future.successful(None))
  when(operationService.findOneBySlug("my-update-operation")).thenReturn(Future.successful(None))
  when(operationService.findOneBySlug("existing-operation-slug")).thenReturn(Future.successful(Some(firstOperation)))
  when(operationService.findOneBySlug("existing-operation-slug-second"))
    .thenReturn(Future.successful(Some(firstOperation.copy(operationId = OperationId("updateOperationId")))))
  when(
    operationService.create(
      userId = tyrion.userId,
      slug = "my-create-operation",
      translations = Seq(OperationTranslation(title = "first create operation", language = "fr")),
      defaultLanguage = "fr",
      sequenceLandingId = SequenceId("29625b5a-56da-4539-b195-15303187c20b"),
      countriesConfiguration = Seq(OperationCountryConfiguration(countryCode = "FR", tagIds = Seq(TagId("hello"))))
    )
  ).thenReturn(Future.successful(OperationId("createdOperationId")))

  when(operationService.findOne(OperationId("updateOperationId"))).thenReturn(Future.successful(Some(firstOperation)))
  when(
    operationService.update(
      operationId = OperationId("updateOperationId"),
      userId = tyrion.userId,
      status = Some(OperationStatus.Active),
      slug = Some("my-update-operation"),
      translations = Some(Seq(OperationTranslation(title = "first update operation", language = "fr"))),
      defaultLanguage = Some("fr"),
      sequenceLandingId = Some(SequenceId("29625b5a-56da-4539-b195-15303187c20b")),
      countriesConfiguration =
        Some(Seq(OperationCountryConfiguration(countryCode = "FR", tagIds = Seq(TagId("hello")))))
    )
  ).thenReturn(Future.successful(Some(OperationId("updateOperationId"))))
  when(
    operationService.update(
      operationId = OperationId("updateOperationId"),
      userId = tyrion.userId,
      status = Some(OperationStatus.Active),
      slug = Some("existing-operation-slug-second"),
      translations = Some(Seq(OperationTranslation(title = "first update operation", language = "fr"))),
      defaultLanguage = Some("fr"),
      sequenceLandingId = Some(SequenceId("29625b5a-56da-4539-b195-15303187c20b")),
      countriesConfiguration =
        Some(Seq(OperationCountryConfiguration(countryCode = "FR", tagIds = Seq(TagId("hello")))))
    )
  ).thenReturn(Future.successful(Some(OperationId("updateOperationId"))))

  when(userService.getUsersByUserIds(Seq(john.userId)))
    .thenReturn(Future.successful(Seq(john)))

  feature("get operations") {

    scenario("get all operations without authentication") {
      Given("2 registered operations")
      When("I get all proposals without authentication")
      Then("I get an unauthorized status response")
      Get("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("get all operations with bad credentials") {
      Given("2 registered operations")
      When("I get all proposals with a citizen role authentication")
      Then("I get a forbidden status response")
      Get("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("get all operations") {
      Given("2 registered operations")
      When("I get all proposals")
      Then("I get a list of 2 operations")
      Get("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val moderationOperationListResponse: ModerationOperationListResponse =
          entityAs[ModerationOperationListResponse]
        moderationOperationListResponse.total should be(2)
        moderationOperationListResponse.results.map { moderationOperationResponse =>
          moderationOperationResponse shouldBe a[ModerationOperationResponse]
        }

        moderationOperationListResponse.results.count(_.operationId.value == "secondOperation") should be(1)
        moderationOperationListResponse.results.count(_.operationId.value == "firstOperation") should be(1)
        val firstOperationResult: ModerationOperationResponse =
          moderationOperationListResponse.results.filter(_.operationId.value == "firstOperation").head
        firstOperationResult.slug should be("first-operation")
        firstOperationResult.translations.filter(_.language == "fr").head.title should be("première operation")
        firstOperationResult.translations.filter(_.language == "en").head.title should be("first operation")
        firstOperationResult.defaultLanguage should be("fr")
        firstOperationResult.sequenceLandingId.value should be("first-sequence-id")
        firstOperationResult.countriesConfiguration.filter(_.countryCode == "BR").head.tagIds should be(Seq.empty)
        firstOperationResult.events.length should be(1)
        firstOperationResult.events.head.user.get shouldBe a[UserResponse]
      }
    }

    scenario("get an operation by slug") {
      Given("2 registered operations")
      When("I get all proposals with a filter by slug")
      Then("I get a list of 1 operation")
      And("the operation match the slug")
      Get("/moderation/operations?slug=second-operation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val moderationOperationListResponse: ModerationOperationListResponse =
          entityAs[ModerationOperationListResponse]
        moderationOperationListResponse.total should be(1)
        moderationOperationListResponse.results.head shouldBe a[ModerationOperationResponse]

        val secondOperationResult: ModerationOperationResponse = moderationOperationListResponse.results.head
        secondOperationResult.slug should be("second-operation")
        secondOperationResult.translations.filter(_.language == "it").head.title should be("secondo operazione")
        secondOperationResult.translations.filter(_.language == "en").head.title should be("second operation")
        secondOperationResult.defaultLanguage should be("it")
        secondOperationResult.sequenceLandingId.value should be("second-sequence-id")
        secondOperationResult.countriesConfiguration.filter(_.countryCode == "IT").head.tagIds should be(Seq.empty)
        secondOperationResult.events.length should be(1)
        secondOperationResult.events.head.user.get shouldBe a[UserResponse]
      }
    }
  }

  feature("get an operation") {

    scenario("get an operation without authentication") {
      Given("2 registered operations")
      When("I get a proposal without authentication")
      Then("I get an unauthorized status response")
      Get("/moderation/operations/firstOperation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, "")) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("get an operation with bad credentials") {
      Given("2 registered operations")
      When("I get a proposal with a citizen role authentication")
      Then("I get a forbidden status response")
      Get("/moderation/operations/firstOperation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("get an operation with invalid id") {
      Given("2 registered operations")
      When("I get a proposal with an invalid id")
      Then("I get a not found status response")
      Get("/moderation/operations/fakeid")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.NotFound)
      }
    }

    scenario("get an operation") {
      Given("2 registered operations")
      When("I get a proposal with a moderation authentication")
      Then("the call success")
      Get("/moderation/operations/firstOperation")
        .withEntity(HttpEntity(ContentTypes.`application/json`, ""))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val firstOperationResult: ModerationOperationResponse =
          entityAs[ModerationOperationResponse]
        firstOperationResult shouldBe a[ModerationOperationResponse]
        firstOperationResult.slug should be("first-operation")
        firstOperationResult.translations.filter(_.language == "fr").head.title should be("première operation")
        firstOperationResult.translations.filter(_.language == "en").head.title should be("first operation")
        firstOperationResult.defaultLanguage should be("fr")
        firstOperationResult.sequenceLandingId.value should be("first-sequence-id")
        firstOperationResult.countriesConfiguration.filter(_.countryCode == "BR").head.tagIds should be(Seq.empty)
        firstOperationResult.events.length should be(1)
        firstOperationResult.events.head.user.get shouldBe a[UserResponse]
      }
    }
  }

  feature("create an operation") {
    scenario("create an operation without authentication") {
      When("I create an operation without authentication")
      Then("I get an unauthorized status response")
      Post("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validCreateJson")) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("create an operation with bad credentials") {
      When("I create a proposal with a citizen role authentication")
      Then("I get a forbidden status response")
      Post("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validCreateJson"))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("create an operation") {
      When("I create a proposal with a moderation role authentication")
      Then("I get a success status")
      And("operation is registered")
      Post("/moderation/operations")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validCreateJson"))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Created)
      }
    }

    scenario("create an operation with an invalid sequenceId") {
      When("I create a proposal with an invalid sequence id")
      Then("I get a bad request status")
      And("a correct error message")
      Post("/moderation/operations")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            "29625b5a-56da-4539-b195-15303187c20b".r.replaceFirstIn(s"$validCreateJson", "fakeSequenceId")
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "sequenceLandingId")
        contentError should be(
          Some(ValidationError("sequenceLandingId", Some("Sequence with id 'fakeSequenceId' not found")))
        )
      }
    }

    scenario("create an operation with an invalid tag") {
      When("I create a proposal with an invalid tag")
      Then("I get a bad request status")
      And("a correct error message")
      Post("/moderation/operations")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, "hello".r.replaceFirstIn(s"$validCreateJson", "fakeTag"))
        )
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "tagIds")
        contentError should be(Some(ValidationError("tagIds", Some("Some tag ids are invalid"))))
      }
    }

    scenario("create an operation with an existing slug") {
      When("I create a proposal with an existing slug")
      Then("I get a bad request status")
      And("a correct error message")
      Post("/moderation/operations")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            "my-create-operation".r.replaceFirstIn(s"$validCreateJson", "existing-operation-slug")
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "slug")
        contentError should be(Some(ValidationError("slug", Some("Slug 'existing-operation-slug' already exist"))))
      }
    }

  }

  feature("update an operation") {
    scenario("create an operation without authentication") {
      Given("a registered operation")
      When("I update the operation without authentication")
      Then("I get an unauthorized status response")
      Put("/moderation/operations/updateOperationId")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validUpdateJson")) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    scenario("create an operation with bad credentials") {
      Given("a registered operation")
      When("I update a proposal with a citizen role authentication")
      Then("I get a forbidden status response")
      Put("/moderation/operations/updateOperationId")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validUpdateJson"))
        .withHeaders(Authorization(OAuth2BearerToken(validAccessToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    scenario("create an operation") {
      When("I create a proposal with a moderation role authentication")
      Then("I get a success status")
      And("operation is registered")
      Put("/moderation/operations/updateOperationId")
        .withEntity(HttpEntity(ContentTypes.`application/json`, s"$validUpdateJson"))
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }

    scenario("update an operation with an invalid sequenceId") {
      When("I update a proposal with an invalid sequence id")
      Then("I get a bad request status")
      And("a correct error message")
      Put("/moderation/operations/updateOperationId")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            "29625b5a-56da-4539-b195-15303187c20b".r.replaceFirstIn(s"$validUpdateJson", "fakeSequenceId")
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "sequenceLandingId")
        contentError should be(
          Some(ValidationError("sequenceLandingId", Some("Sequence with id 'fakeSequenceId' not found")))
        )
      }
    }

    scenario("update an operation with an invalid tag") {
      When("I update a proposal with an invalid tag")
      Then("I get a bad request status")
      And("a correct error message")
      Put("/moderation/operations/updateOperationId")
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, "hello".r.replaceFirstIn(s"$validUpdateJson", "fakeTag"))
        )
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "tagIds")
        contentError should be(Some(ValidationError("tagIds", Some("Some tag ids are invalid"))))
      }
    }

    scenario("update an operation with an existing slug") {
      When("I update a proposal with an existing slug")
      Then("I get a bad request status")
      And("a correct error message")
      Put("/moderation/operations/updateOperationId")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            "my-update-operation".r.replaceFirstIn(s"$validUpdateJson", "existing-operation-slug")
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val contentError = errors.find(_.field == "slug")
        contentError should be(Some(ValidationError("slug", Some("Slug 'existing-operation-slug' already exist"))))
      }
    }

    scenario("update an operation with his own slug") {
      When("I update a proposal with his own slug")
      Then("I get a success status")
      And("any error message")
      Put("/moderation/operations/updateOperationId")
        .withEntity(
          HttpEntity(
            ContentTypes.`application/json`,
            "my-update-operation".r.replaceFirstIn(s"$validUpdateJson", "existing-operation-slug-second")
          )
        )
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }
}
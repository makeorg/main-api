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

package org.make.api.user

import java.net.InetAddress
import java.time.{Instant, LocalDate}
import java.util.Date

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Remote-Address`, Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.Route
import akka.util.ByteString
import com.sksamuel.elastic4s.searches.sort.SortOrder.Desc
import org.make.api.extensions.MakeSettingsComponent
import org.make.api.proposal.{
  ProposalResponse,
  ProposalService,
  ProposalServiceComponent,
  ProposalsResultSeededResponse,
  _
}
import org.make.api.question.{QuestionService, QuestionServiceComponent}
import org.make.api.sessionhistory.SessionHistoryCoordinatorServiceComponent
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.technical._
import org.make.api.technical.auth.AuthenticationApi.TokenResponse
import org.make.api.technical.auth._
import org.make.api.technical.storage.Content.FileContent
import org.make.api.technical.storage._
import org.make.api.user.UserExceptions.EmailAlreadyRegisteredException
import org.make.api.user.social._
import org.make.api.userhistory.{ResetPasswordEvent, UserHistoryCoordinatorServiceComponent}
import org.make.api.{ActorSystemComponent, MakeApi, MakeApiTestBase, TestUtils}
import org.make.core.auth.{ClientId, UserRights}
import org.make.core.common.indexed.Sort
import org.make.core.operation.OperationId
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user._
import org.make.core.{DateHelper, RequestContext, ValidationError}
import scalaoauth2.provider.{AccessToken, AuthInfo}

import scala.collection.immutable.Seq
import scala.concurrent.Future

class UserApiTest
    extends MakeApiTestBase
    with DefaultUserApiComponent
    with QuestionServiceComponent
    with ProposalServiceComponent
    with UserServiceComponent
    with MakeDataHandlerComponent
    with IdGeneratorComponent
    with SocialServiceComponent
    with SessionHistoryCoordinatorServiceComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent
    with PersistentUserServiceComponent
    with EventBusServiceComponent
    with MakeSettingsComponent
    with ActorSystemComponent
    with StorageServiceComponent
    with StorageConfigurationComponent {

  override val userService: UserService = mock[UserService]
  override val persistentUserService: PersistentUserService = mock[PersistentUserService]
  override val socialService: SocialService = mock[SocialService]
  override val facebookApi: FacebookApi = mock[FacebookApi]
  override val googleApi: GoogleApi = mock[GoogleApi]
  override val proposalService: ProposalService = mock[ProposalService]
  override val questionService: QuestionService = mock[QuestionService]
  override val storageService: StorageService = mock[StorageService]
  override val storageConfiguration: StorageConfiguration = mock[StorageConfiguration]

  private val authenticationConfiguration = mock[makeSettings.Authentication.type]

  when(makeSettings.Authentication).thenReturn(authenticationConfiguration)
  when(authenticationConfiguration.defaultClientId).thenReturn("default-client")
  when(idGenerator.nextId()).thenReturn("some-id")

  override val proposalJournal: MakeReadJournal = mock[MakeReadJournal]
  override val userJournal: MakeReadJournal = mock[MakeReadJournal]
  override val sessionJournal: MakeReadJournal = mock[MakeReadJournal]

  val routes: Route = sealRoute(handleRejections(MakeApi.rejectionHandler) {
    userApi.routes
  })

  val expiresInSecond = 1000

  val fakeUser: User = TestUtils.user(
    id = UserId("ABCD"),
    email = "foo@bar.com",
    firstName = Some("olive"),
    lastName = Some("tom"),
    lastIp = Some("127.0.0.1"),
    hashedPassword = Some("passpass"),
    emailVerified = false,
    lastConnection = DateHelper.now(),
    verificationToken = Some("token"),
    verificationTokenExpiresAt = Some(DateHelper.now())
  )

  val sylvain: User =
    fakeUser.copy(userId = UserId("sylvain-user-id"), email = "sylvain@example.com", firstName = Some("Sylvain"))
  val vincent: User =
    fakeUser.copy(userId = UserId("vincent-user-id"), email = "vincent@example.com", firstName = Some("Vincent"))

  val citizenToken: String = "TOKEN_CITIZEN"
  val citizenAccessToken: AccessToken =
    AccessToken("ACCESS_TOKEN_CITIZEN", None, None, None, Date.from(Instant.now))
  val citizenFakeAuthInfo: AuthInfo[UserRights] =
    AuthInfo(
      UserRights(
        userId = sylvain.userId,
        roles = Seq(Role.RoleCitizen),
        availableQuestions = Seq.empty,
        emailVerified = true
      ),
      None,
      None,
      None
    )

  val moderatorToken: String = "TOKEN_MODERATOR"
  val moderatorAccessToken: AccessToken =
    AccessToken("ACCESS_TOKEN_MODERATOR", None, None, None, Date.from(Instant.now))
  val moderatorFakeAuthInfo: AuthInfo[UserRights] =
    AuthInfo(
      UserRights(
        userId = vincent.userId,
        roles = Seq(Role.RoleModerator, Role.RoleCitizen),
        availableQuestions = Seq.empty,
        emailVerified = true
      ),
      None,
      None,
      None
    )

  val adminToken: String = "TOKEN_ADMIN"
  val adminAccessToken: AccessToken =
    AccessToken("ACCESS_TOKEN_ADMIN", None, None, None, Date.from(Instant.now))
  val adminFakeAuthInfo: AuthInfo[UserRights] =
    AuthInfo(
      UserRights(
        userId = vincent.userId,
        roles = Seq(Role.RoleAdmin, Role.RoleModerator, Role.RoleCitizen),
        availableQuestions = Seq.empty,
        emailVerified = true
      ),
      None,
      None,
      None
    )

  reset(oauth2DataHandler)

  when(oauth2DataHandler.findAccessToken(same(citizenToken)))
    .thenReturn(Future.successful(Some(citizenAccessToken)))
  when(oauth2DataHandler.findAuthInfoByAccessToken(same(citizenAccessToken)))
    .thenReturn(Future.successful(Some(citizenFakeAuthInfo)))
  when(oauth2DataHandler.findAccessToken(same(moderatorToken)))
    .thenReturn(Future.successful(Some(moderatorAccessToken)))
  when(oauth2DataHandler.findAuthInfoByAccessToken(same(moderatorAccessToken)))
    .thenReturn(Future.successful(Some(moderatorFakeAuthInfo)))
  when(oauth2DataHandler.findAccessToken(same(adminToken)))
    .thenReturn(Future.successful(Some(adminAccessToken)))
  when(oauth2DataHandler.findAuthInfoByAccessToken(same(adminAccessToken)))
    .thenReturn(Future.successful(Some(adminFakeAuthInfo)))

  val publicUser = fakeUser.copy(userId = UserId("EFGH"), publicProfile = true)

  val users = Map(
    sylvain.userId -> sylvain,
    vincent.userId -> vincent,
    fakeUser.userId -> fakeUser,
    publicUser.userId -> publicUser
  )
  when(userService.getUser(any[UserId])).thenAnswer { userId: UserId =>
    Future.successful(users.get(userId))
  }

  when(userService.update(any[User], any[RequestContext])).thenAnswer { user: User =>
    Future.successful(user)
  }

  when(
    userService
      .followUser(any[UserId], any[UserId], any[RequestContext])
  ).thenAnswer { id: UserId =>
    Future.successful(id)
  }

  when(
    userService
      .unfollowUser(any[UserId], any[UserId], any[RequestContext])
  ).thenAnswer { id: UserId =>
    Future.successful(id)
  }

  Feature("register user") {

    when(questionService.getQuestion(any[QuestionId]))
      .thenReturn(
        Future.successful(
          Some(
            Question(
              operationId = Some(OperationId("operation1")),
              questionId = QuestionId("thequestionid"),
              slug = "the-question",
              country = Country("FR"),
              language = Language("fr"),
              question = "question ?",
              shortTitle = None
            )
          )
        )
      )

    Scenario("successful register user") {
      when(
        userService
          .register(any[UserRegisterData], any[RequestContext])
      ).thenReturn(Future.successful(fakeUser))
      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02",
          | "gender": "M",
          | "socioProfessionalCategory": "EMPL",
          | "optInPartner": true,
          | "questionId": "thequestionid",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        verify(userService).register(
          eqTo(
            UserRegisterData(
              email = "foo@bar.com",
              firstName = Some("olive"),
              lastName = Some("tom"),
              password = Some("mypassss"),
              lastIp = Some("192.0.0.1"),
              dateOfBirth = Some(LocalDate.parse("1997-12-02")),
              country = Country("FR"),
              language = Language("fr"),
              gender = Some(Gender.Male),
              socioProfessionalCategory = Some(SocioProfessionalCategory.Employee),
              optInPartner = Some(true),
              questionId = Some(QuestionId("thequestionid"))
            )
          ),
          any[RequestContext]
        )
      }
    }

    Scenario("successful register user from minor child") {
      when(
        userService
          .register(any[UserRegisterData], any[RequestContext])
      ).thenReturn(Future.successful(fakeUser))
      val dateOfBirth = LocalDate.now().minusYears(9L)
      val request =
        s"""
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "$dateOfBirth",
          | "gender": "M",
          | "socioProfessionalCategory": "EMPL",
          | "optInPartner": true,
          | "questionId": "thequestionid",
          | "country": "FR",
          | "language": "fr",
          | "legalMinorConsent": true,
          | "legalAdvisorApproval": true
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        verify(userService).register(
          eqTo(
            UserRegisterData(
              email = "foo@bar.com",
              firstName = Some("olive"),
              lastName = Some("tom"),
              password = Some("mypassss"),
              lastIp = Some("192.0.0.1"),
              dateOfBirth = Some(dateOfBirth),
              country = Country("FR"),
              language = Language("fr"),
              gender = Some(Gender.Male),
              socioProfessionalCategory = Some(SocioProfessionalCategory.Employee),
              optInPartner = Some(true),
              questionId = Some(QuestionId("thequestionid")),
              legalMinorConsent = Some(true),
              legalAdvisorApproval = Some(true)
            )
          ),
          any[RequestContext]
        )
      }
    }

    Scenario("successful register user from proposal context") {
      when(
        userService
          .register(any[UserRegisterData], any[RequestContext])
      ).thenReturn(Future.successful(fakeUser))
      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02",
          | "postalCode": "75011",
          | "profession": "football player",
          | "country": "FR",
          | "language": "fr",
          | "politicalParty": "PP",
          | "website":"http://example.com"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        verify(userService).register(
          eqTo(
            UserRegisterData(
              email = "foo@bar.com",
              firstName = Some("olive"),
              lastName = Some("tom"),
              password = Some("mypassss"),
              lastIp = Some("192.0.0.1"),
              dateOfBirth = Some(LocalDate.parse("1997-12-02")),
              postalCode = Some("75011"),
              profession = Some("football player"),
              country = Country("FR"),
              language = Language("fr"),
              questionId = None,
              politicalParty = Some("PP"),
              website = Some("http://example.com")
            )
          ),
          any[RequestContext]
        )
      }
    }

    Scenario("validation failed for existing email") {
      when(
        userService
          .register(any[UserRegisterData], any[RequestContext])
      ).thenReturn(Future.failed(EmailAlreadyRegisteredException("foo@bar.com")))

      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(
          Some(ValidationError("email", "already_registered", Some("Email foo@bar.com already exist")))
        )
      }
    }

    Scenario("validation failed for missing country and/or language") {
      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val countryError = errors.find(_.field == "country")
        countryError should be(Some(ValidationError("country", "mandatory", Some("country is mandatory"))))
        val languageError = errors.find(_.field == "language")
        languageError should be(Some(ValidationError("language", "mandatory", Some("language is mandatory"))))
      }
    }

    Scenario("validation failed for malformed email") {
      val request =
        """
          |{
          | "email": "foo",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", "invalid_email", Some("email is not a valid email"))))
      }
    }

    Scenario("validation failed for postal code invalid") {
      val request =
        """
          |{
          | "email": "foo@bar.com",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "1997-12-02",
          | "postalCode": "A0123",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "postalCode")
        emailError should be(
          Some(
            ValidationError("postalCode", "invalid_postal_code", Some("Invalid postal code. Must be formatted '01234'"))
          )
        )
      }
    }

    Scenario("validation failed for malformed date of birth") {
      val request =
        """
          |{
          | "email": "foo",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "foo-12-02",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val dateOfBirthError = errors.find(_.field == "dateOfBirth")
        dateOfBirthError should be(
          Some(
            ValidationError(
              "dateOfBirth",
              "malformed",
              Some("foo-12-02 is not a valid date, it should match yyyy-MM-dd")
            )
          )
        )
      }
    }

    Scenario("validation failed for invalid gender") {
      val request =
        """
          |{
          | "email": "foo",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "country": "FR",
          | "language": "fr",
          | "gender": "S"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val genderError = errors.find(_.field == "gender")
        genderError.map(_.field) should be(Some("gender"))
        genderError.map(_.key) should be(Some("malformed"))
        // TODO have some nicer error message
      }
    }

    Scenario("validation failed for invalid website") {
      val request =
        """
          |{
          | "email": "foo",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "country": "FR",
          | "language": "fr",
          | "website": "fake website"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val genderError = errors.find(_.field == "website")
        genderError should be(
          Some(ValidationError("website", "malformed", Some("Url predicate failed: no protocol: fake website")))
        )
      }
    }

    Scenario("validation failed for required field") {
      // todo: add parser of error messages
      val request =
        """
          |{
          | "email": "foo@bar.com",
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request)) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("validation failed for invalid date of birth") {
      val underage: LocalDate = LocalDate.now().minusYears(5L)
      val overage: LocalDate = LocalDate.now().minusYears(121L)
      def request(dateOfBirth: LocalDate) =
        s"""
          |{
          | "email": "foo@baz.fr",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "${dateOfBirth.toString}",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request(underage))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val dateOfBirthError = errors.find(_.field == "dateOfBirth")
        dateOfBirthError should be(
          Some(ValidationError("dateOfBirth", "invalid_age", Some("Invalid date: age must be between 8 and 120")))
        )
      }
      Post("/user", HttpEntity(ContentTypes.`application/json`, request(overage))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val dateOfBirthError = errors.find(_.field == "dateOfBirth")
        dateOfBirthError should be(
          Some(ValidationError("dateOfBirth", "invalid_age", Some("Invalid date: age must be between 8 and 120")))
        )
      }
    }

    Scenario("validation failed for invalid consent from minor child") {
      val nineYearsOld: LocalDate = LocalDate.now().minusYears(9L)
      def request(dateOfBirth: LocalDate) =
        s"""
          |{
          | "email": "foo@baz.fr",
          | "firstName": "olive",
          | "lastName": "tom",
          | "password": "mypassss",
          | "dateOfBirth": "${dateOfBirth.toString}",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      Post("/user", HttpEntity(ContentTypes.`application/json`, request(nineYearsOld))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val legalMinorConsentError = errors.find(_.field == "legalMinorConsent")
        val legalAdvisorApprovalError = errors.find(_.field == "legalAdvisorApproval")
        legalMinorConsentError should be(
          Some(ValidationError("legalMinorConsent", "legal_consent", Some("Field legalMinorConsent must be approved.")))
        )
        legalAdvisorApprovalError should be(
          Some(
            ValidationError(
              "legalAdvisorApproval",
              "legal_consent",
              Some("Field legalAdvisorApproval must be approved.")
            )
          )
        )
      }
    }
  }

  Feature("login user from social") {
    Scenario("successful login user") {
      when(
        socialService
          .login(
            any[String],
            any[String],
            any[Country],
            any[Language],
            any[Option[String]],
            any[Option[QuestionId]],
            any[RequestContext],
            any[ClientId]
          )
      ).thenReturn(
        Future.successful(
          (
            UserId("12347"),
            SocialLoginResponse(
              TokenResponse(
                token_type = "Bearer",
                access_token = "access_token",
                expires_in = expiresInSecond,
                refresh_token = "refresh_token"
              ),
              false
            )
          )
        )
      )
      val request =
        """
          |{
          | "provider": "google",
          | "token": "ABCDEFGHIJK",
          | "country": "FR",
          | "language": "fr"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user/login/social", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Created)
        header("Set-Cookie").get.value should include("cookie-secure")
        verify(socialService).login(
          eqTo("google"),
          eqTo("ABCDEFGHIJK"),
          eqTo(Country("FR")),
          eqTo(Language("fr")),
          eqTo(Some("192.0.0.1")),
          any[Option[QuestionId]],
          any[RequestContext],
          any[ClientId]
        )
      }
    }

    Scenario("bad request when login social user") {
      when(
        socialService
          .login(
            any[String],
            any[String],
            any[Country],
            any[Language],
            any[Option[String]],
            any[Option[QuestionId]],
            any[RequestContext],
            any[ClientId]
          )
      ).thenReturn(
        Future.successful(
          (
            UserId("12347"),
            SocialLoginResponse(
              TokenResponse(
                token_type = "Bearer",
                access_token = "access_token",
                expires_in = expiresInSecond,
                refresh_token = "refresh_token"
              ),
              false
            )
          )
        )
      )
      val request =
        """
          |{
          | "provider": "google",
          | "clientId": "client-id"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")
      Post("/user/login/social", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }
  }

  Feature("reset password") {
    info("In order to reset a password")
    info("As a user with an email")
    info("I want to use api to reset my password")

    val johnDoeUser = TestUtils.user(
      id = UserId("JOHNDOE"),
      email = "john.doe@example.com",
      firstName = Some("John"),
      lastName = Some("Doe"),
      lastIp = Some("127.0.0.1"),
      hashedPassword = Some("passpass"),
      emailVerified = false,
      lastConnection = DateHelper.now(),
      verificationToken = Some("token"),
      verificationTokenExpiresAt = Some(DateHelper.now())
    )
    when(persistentUserService.findByEmail("john.doe@example.com"))
      .thenReturn(Future.successful(Some(johnDoeUser)))
    when(persistentUserService.findUserIdByEmail("invalidexample.com"))
      .thenThrow(new IllegalArgumentException("findUserIdByEmail should be called with valid email"))
    when(eventBusService.publish(any[ResetPasswordEvent])).thenAnswer { event: ResetPasswordEvent =>
      if (!event.userId.equals(johnDoeUser.userId)) {
        throw new IllegalArgumentException("UserId not match")
      }
    }
    when(persistentUserService.findByEmail("fake@example.com"))
      .thenReturn(Future.successful(None))

    val fooBarUserId = UserId("foo-bar")
    val fooBarUser = TestUtils.user(
      id = fooBarUserId,
      email = "foo@exemple.com",
      firstName = None,
      lastName = None,
      lastConnection = DateHelper.now(),
      resetToken = Some("baz-bar"),
      resetTokenExpiresAt = Some(DateHelper.now().minusDays(1))
    )

    val notExpiredResetTokenUserId: UserId = UserId("not-expired-reset-token-user-id")
    val validResetToken: String = "valid-reset-token"
    val notExpiredResetTokenUser = TestUtils.user(
      id = notExpiredResetTokenUserId,
      email = "foo@exemple.com",
      firstName = None,
      lastName = None,
      lastConnection = DateHelper.now(),
      resetToken = Some("valid-reset-token"),
      resetTokenExpiresAt = Some(DateHelper.now().plusDays(1))
    )

    when(persistentUserService.findUserByUserIdAndResetToken(fooBarUserId, "baz-bar"))
      .thenReturn(Future.successful(Some(fooBarUser)))
    when(persistentUserService.findUserByUserIdAndResetToken(fooBarUserId, "bad-bad"))
      .thenReturn(Future.successful(None))
    when(persistentUserService.findUserByUserIdAndResetToken(UserId("bad-foo"), "baz-bar"))
      .thenReturn(Future.successful(None))
    when(persistentUserService.findUserByUserIdAndResetToken(notExpiredResetTokenUserId, validResetToken))
      .thenReturn(Future.successful(Some(notExpiredResetTokenUser)))

    when(userService.requestPasswordReset(any[UserId])).thenReturn(Future.successful(true))

    Scenario("Reset a password from an existing email") {
      Given("a registered user with an email john.doe@example.com")
      When("I reset password with john.doe@example.com")
      val request =
        """
          |{
          | "email": "john.doe@example.com"
          |}
        """.stripMargin

      val resetPasswordRequestRoute = Post(
        "/user/reset-password/request-reset",
        HttpEntity(ContentTypes.`application/json`, request)
      ) ~> routes

      Then("The existence of email is checked")
      And("I get a valid response")
      resetPasswordRequestRoute ~> check {
        verify(eventBusService, times(1)).publish(any[ResetPasswordEvent])
        status should be(StatusCodes.NoContent)
      }
      And("a user Event ResetPasswordEvent is emitted")
    }

    Scenario("Reset a password from an nonexistent email") {
      Given("an nonexistent email fake@example.com")
      When("I reset password with fake@example.com")
      val request =
        """
          |{
          | "email": "fake@example.com"
          |}
        """.stripMargin

      val resetPasswordRequestRoute = Post(
        "/user/reset-password/request-reset",
        HttpEntity(ContentTypes.`application/json`, request)
      ) ~> routes

      Then("The existence of email is checked")
      And("I get a not found response")
      resetPasswordRequestRoute ~> check {
        status should be(StatusCodes.NotFound)
      }
      And("any user Event ResetPasswordEvent is emitted")
    }

    Scenario("Reset a password from an invalid email") {
      Given("an invalid email invalidexample.com")
      When("I reset password with invalidexample.com")
      val request =
        """
          |{
          | "email": "invalidexample.com"
          |}
        """.stripMargin

      val resetPasswordRequestRoute = Post(
        "/user/reset-password/request-reset",
        HttpEntity(ContentTypes.`application/json`, request)
      ) ~> routes

      Then("The existence of email is not checked")
      And("I get a bad request response")
      resetPasswordRequestRoute ~> check {
        status should be(StatusCodes.BadRequest)
        val errors = entityAs[Seq[ValidationError]]
        val emailError = errors.find(_.field == "email")
        emailError should be(Some(ValidationError("email", "invalid_email", Some("email is not a valid email"))))
      }
      And("any user Event ResetPasswordEvent is emitted")

    }

    Scenario("Check a reset token from an existing user") {
      Given("a registered user with an uuid not-expired-reset-token-user-id and a reset token valid-reset-token")
      When("I check that reset token is for the right user")

      val resetPasswordCheckRoute = Post(
        "/user/reset-password/check-validity/not-expired-reset-token-user-id/valid-reset-token",
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> routes

      Then("The reset Token is for the right passed user and the reset token is not expired")
      And("I get a valid response")
      resetPasswordCheckRoute ~> check {
        status should be(StatusCodes.NoContent)
      }

      And("an empty result is returned")
    }

    Scenario("Check an expired reset token from an existing user") {
      Given("a registered user with an uuid foo-bar and a reset token baz-bar")
      When("I check that reset token is for the right user and is not expired")

      val resetPasswordCheckRoute = Post(
        "/user/reset-password/check-validity/foo-bar/baz-bar",
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> routes

      Then("The reset Token is for the right passed user but is expired")
      And("I get a valid response")
      resetPasswordCheckRoute ~> check {
        status should be(StatusCodes.BadRequest)
      }

      And("an empty result is returned")
    }

    Scenario("Check a bad reset token from an existing user") {
      Given("a registered user with an uuid foo-bar and a reset token baz-bar")
      When("I check that reset token is for the right user")

      val resetPasswordCheckRoute = Post(
        "/user/reset-password-check/foo-bar/bad-bad",
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> routes

      Then("The user with this reset Token and userId is not found")
      And("I get a not found response")
      resetPasswordCheckRoute ~> check {
        status should be(StatusCodes.NotFound)
      }
      And("a not found result is returned")
    }

    Scenario("Check a reset token with a bad user") {
      Given("a registered user with an uuid bad-foo and a reset token baz-bar")
      When("I check that reset token is for the right user")

      val resetPasswordCheckRoute = Post(
        "/user/reset-password/check-validity/bad-foo/baz-bar",
        HttpEntity(ContentTypes.`application/json`, "")
      ) ~> routes

      Then("The user with this reset Token and userId")
      And("I get a not found response")
      resetPasswordCheckRoute ~> check {
        status should be(StatusCodes.NotFound)
      }
      And("a not found result is returned")
    }

    Scenario("reset the password of a valid user with valid token") {
      when(
        userService
          .updatePassword(notExpiredResetTokenUserId, Some(validResetToken), "mynewpassword")
      ).thenReturn(Future.successful(true))

      Given("a registered user with an uuid not-expired-reset-token-user-id and a reset token valid-reset-token")
      When("I check that reset token is for the right user")

      val data =
        """
          |{
          | "resetToken": "valid-reset-token",
          | "password": "mynewpassword"
          |}
        """.stripMargin

      val resetPasswordRoute = Post(
        "/user/reset-password/change-password/not-expired-reset-token-user-id",
        HttpEntity(ContentTypes.`application/json`, data)
      ) ~> routes

      Then("The user with this reset Token and userId update his password")
      And("I get a successful response")
      resetPasswordRoute ~> check {
        status should be(StatusCodes.NoContent)
      }
      And("a success result is returned")
    }
  }

  Feature("get the connected user") {
    Scenario("no auth token") {
      Get("/user/me") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("valid token") {
      when(userService.getFollowedUsers(any[UserId])).thenReturn(Future.successful(Seq.empty))

      Get("/user/me").withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[UserResponse]
        result.userId shouldBe sylvain.userId
      }
    }

    Scenario("no auth token from current user") {
      Get("/user/current") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("valid token from current user") {

      Get("/user/current").withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[CurrentUserResponse]
        result.userId shouldBe sylvain.userId
      }
    }
  }

  Feature("get voted proposals of an user") {

    val indexedProposal1 = IndexedProposal(
      id = ProposalId("proposal-1"),
      country = Country("FR"),
      language = Language("fr"),
      userId = sylvain.userId,
      content = "Il faut que ma proposition d'opération soit en CSV.",
      slug = "il-faut-que-ma-proposition-d-operation-soit-en-csv",
      createdAt = DateHelper.now(),
      updatedAt = Some(DateHelper.now()),
      votesCount = 0,
      votesVerifiedCount = 0,
      votesSequenceCount = 0,
      votesSegmentCount = 0,
      toEnrich = false,
      votes = Seq.empty,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = None,
      trending = None,
      labels = Seq.empty,
      author = IndexedAuthor(
        firstName = Some("Paul"),
        displayName = Some("Paul"),
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("11111"),
        age = Some(26),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Accepted,
      ideaId = None,
      operationId = Some(OperationId("operation1")),
      question = None,
      sequencePool = SequencePool.New,
      sequenceSegmentPool = SequencePool.New,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    )
    val indexedProposal2 =
      indexedProposal1.copy(id = ProposalId("proposal-2"), operationId = Some(OperationId("operation2")))
    val indexedProposal3 =
      indexedProposal1.copy(id = ProposalId("proposal-3"), operationId = None)
    val proposalsResult: Seq[ProposalResponse] =
      Seq(
        ProposalResponse(
          indexedProposal = indexedProposal1,
          myProposal = false,
          voteAndQualifications = None,
          proposalKey = "pr0p0541k3y"
        ),
        ProposalResponse(
          indexedProposal = indexedProposal2,
          myProposal = false,
          voteAndQualifications = None,
          proposalKey = "pr0p0541k3y"
        ),
        ProposalResponse(
          indexedProposal = indexedProposal3,
          myProposal = false,
          voteAndQualifications = None,
          proposalKey = "pr0p0541k3y"
        )
      )

    when(
      proposalService
        .searchProposalsVotedByUser(
          userId = eqTo(sylvain.userId),
          filterVotes = eqTo(None),
          filterQualifications = eqTo(None),
          sort = eqTo(Some(Sort(field = Some("createdAt"), mode = Some(Desc)))),
          limit = eqTo(None),
          skip = eqTo(None),
          requestContext = any[RequestContext]
        )
    ).thenReturn(Future.successful(ProposalsResultResponse(total = 3, results = proposalsResult)))
    when(
      proposalService
        .searchProposalsVotedByUser(
          userId = eqTo(vincent.userId),
          filterVotes = eqTo(None),
          filterQualifications = eqTo(None),
          sort = eqTo(Some(Sort(field = Some("createdAt"), mode = Some(Desc)))),
          limit = eqTo(None),
          skip = eqTo(None),
          requestContext = any[RequestContext]
        )
    ).thenReturn(Future.successful(ProposalsResultResponse(total = 0, results = Seq.empty)))

    Scenario("not authenticated") {
      Get("/user/sylvain-user-id/votes") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("authenticated but userId parameter is different than connected user id") {
      Get("/user/xxxxxxxxxxxx/votes").withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("authenticated with empty voted proposals") {
      Get("/user/vincent-user-id/votes").withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[ProposalsResultResponse]
        result.total should be(0)
        result.results should be(Seq.empty)
      }
    }

    Scenario("authenticated with some voted proposals") {
      Get("/user/sylvain-user-id/votes")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[ProposalsResultResponse]
        result.results.map(_.id) should contain(ProposalId("proposal-1"))
      }
    }
  }

  Feature("get user proposals") {

    val indexedProposal1 = IndexedProposal(
      id = ProposalId("333333-3333-3333-3333-33333333"),
      country = Country("FR"),
      language = Language("fr"),
      userId = sylvain.userId,
      content = "Il faut une proposition de Sylvain",
      slug = "il-faut-une-proposition-de-sylvain",
      createdAt = DateHelper.now(),
      updatedAt = Some(DateHelper.now()),
      votes = Seq.empty,
      votesCount = 0,
      votesVerifiedCount = 0,
      votesSequenceCount = 0,
      votesSegmentCount = 0,
      toEnrich = false,
      scores = IndexedScores.empty,
      segmentScores = IndexedScores.empty,
      context = None,
      trending = None,
      labels = Seq.empty,
      author = IndexedAuthor(
        firstName = sylvain.firstName,
        displayName = sylvain.displayName,
        organisationName = None,
        organisationSlug = None,
        postalCode = Some("11111"),
        age = Some(22),
        avatarUrl = None,
        anonymousParticipation = false,
        userType = UserType.UserTypeUser
      ),
      organisations = Seq.empty,
      tags = Seq.empty,
      selectedStakeTag = None,
      status = ProposalStatus.Accepted,
      ideaId = None,
      operationId = Some(OperationId("operation1")),
      question = None,
      sequencePool = SequencePool.New,
      sequenceSegmentPool = SequencePool.New,
      initialProposal = false,
      refusalReason = None,
      operationKind = None,
      segment = None
    )
    val indexedProposal2 = indexedProposal1.copy(operationId = Some(OperationId("operation2")))
    val indexedProposal3 = indexedProposal1.copy(operationId = None)
    val proposalsResult: Seq[ProposalResponse] =
      Seq(
        ProposalResponse(
          indexedProposal = indexedProposal1,
          myProposal = true,
          voteAndQualifications = None,
          proposalKey = "pr0p0541k3y"
        ),
        ProposalResponse(
          indexedProposal = indexedProposal2,
          myProposal = true,
          voteAndQualifications = None,
          proposalKey = "pr0p0541k3y"
        ),
        ProposalResponse(
          indexedProposal = indexedProposal3,
          myProposal = true,
          voteAndQualifications = None,
          proposalKey = "pr0p0541k3y"
        )
      )

    when(
      proposalService
        .searchForUser(
          userId = any[Option[UserId]],
          query = eqTo(
            SearchQuery(
              filters = Some(
                SearchFilters(
                  user = Some(UserSearchFilter(userId = sylvain.userId)),
                  status = Some(StatusSearchFilter(status = ProposalStatus.values.filter(_ != ProposalStatus.Archived)))
                )
              ),
              sort = Some(Sort(field = Some("createdAt"), mode = Some(Desc)))
            )
          ),
          requestContext = any[RequestContext]
        )
    ).thenReturn(Future.successful(ProposalsResultSeededResponse(total = 1, results = proposalsResult, seed = None)))

    Scenario("not authenticated") {
      Get("/user/sylvain-user-id/proposals") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("authenticated but userId parameter is different than connected user id") {
      Get("/user/vincent-user-id/proposals").withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("authenticated with some proposals") {
      Get("/user/sylvain-user-id/proposals?sort=createdAt&order=desc")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[ProposalsResultSeededResponse]
        result.total should be(1)
        result.results.head.id should be(indexedProposal1.id)
      }
    }
  }

  Feature("update a user") {
    Scenario("authentificated with unauthorized user") {

      when(userService.getUser(eqTo(UserId("BAD")))).thenReturn(Future.successful(Some(fakeUser)))

      val request =
        """
          |{
          | "firstName": "unauthorized",
          | "lastName": "user",
          | "dateOfBirth": "1997-12-02",
          | "country": "FR",
          | "language": "fr",
          | "gender": "F",
          | "socioProfessionalCategory": "EMPL"
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")

      Patch("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr))) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("successful update user") {
      when(userService.getUser(eqTo(UserId("ABCD")))).thenReturn(Future.successful(Some(fakeUser)))

      val request =
        """
          |{
          | "firstName": "olive",
          | "lastName": "tom",
          | "dateOfBirth": "1997-12-02",
          | "country": "IT",
          | "language": "it",
          | "gender": "F",
          | "socioProfessionalCategory": "EMPL",
          | "postalCode": ""
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")

      Patch("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr)))
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        verify(userService).update(
          eqTo(
            sylvain.copy(
              firstName = Some("olive"),
              lastName = Some("tom"),
              country = Country("IT"),
              language = Language("it"),
              profile = Profile.parseProfile(
                dateOfBirth = Some(LocalDate.parse("1997-12-02")),
                optInNewsletter = false,
                gender = Some(Gender.Female),
                socioProfessionalCategory = Some(SocioProfessionalCategory.Employee)
              )
            )
          ),
          any[RequestContext]
        )
      }
    }

    Scenario("user remove age, gender, csp, website from the front") {
      val request =
        """
          |{
          | "firstName": "olive",
          | "lastName": "tom",
          | "dateOfBirth": "",
          | "country": "IT",
          | "language": "it",
          | "gender": "",
          | "socioProfessionalCategory": "",
          | "website": ""
          |}
        """.stripMargin

      val addr: InetAddress = InetAddress.getByName("192.0.0.1")

      Patch("/user", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(`Remote-Address`(RemoteAddress(addr)))
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        verify(userService).update(
          eqTo(
            sylvain.copy(
              firstName = Some("olive"),
              lastName = Some("tom"),
              country = Country("IT"),
              language = Language("it"),
              profile = Profile.parseProfile(
                dateOfBirth = None,
                optInNewsletter = false,
                gender = None,
                socioProfessionalCategory = None,
                website = None
              )
            )
          ),
          any[RequestContext]
        )
      }
    }
  }

  Feature("modify user password") {

    val fakeRequest =
      """
        |{
        | "actualPassword": "",
        | "newPassword": "12345678"
        |}
      """.stripMargin

    Scenario("unauthenticated user") {
      Post("/user/sylvain-user-id/change-password", HttpEntity(ContentTypes.`application/json`, fakeRequest)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("authenticated but userId parameter is different than connected user id") {
      Post("/user/vincent-user-id/change-password", HttpEntity(ContentTypes.`application/json`, fakeRequest))
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("social login user change password") {

      val request =
        """
          |{
          | "newPassword": "mynewpassword"
          |}
        """.stripMargin

      when(
        userService
          .getUserByUserIdAndPassword(any[UserId], same(None))
      ).thenReturn(Future.successful(Some(sylvain)))

      when(
        userService
          .updatePassword(any[UserId], same(None), any[String])
      ).thenReturn(Future.successful(true))

      Post("/user/sylvain-user-id/change-password", HttpEntity(ContentTypes.`application/json`, request))
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  Feature("delete user") {

    val fakeRequest =
      """
        | {
        |   "password": "mypassword"
        | }
      """.stripMargin

    Scenario("unauthenticated user") {
      Post("/user/sylvain-user-id/delete", HttpEntity(ContentTypes.`application/json`, fakeRequest)) ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("authenticated but userId parameter is different than connected user id") {
      Post("/user/vincent-user-id/delete", HttpEntity(ContentTypes.`application/json`, fakeRequest))
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("wrong password") {
      when(
        userService
          .getUserByUserIdAndPassword(any[UserId], any[Option[String]])
      ).thenReturn(Future.successful(None))

      Post("/user/sylvain-user-id/delete", HttpEntity(ContentTypes.`application/json`, fakeRequest))
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("user request anonymization") {
      when(
        userService
          .getUserByUserIdAndPassword(any[UserId], any[Option[String]])
      ).thenReturn(Future.successful(Some(sylvain)))

      when(
        userService
          .anonymize(any[User], any[UserId], any[RequestContext])
      ).thenReturn(Future.successful({}))

      when(oauth2DataHandler.removeTokenByUserId(any[UserId]))
        .thenReturn(Future.successful(1))

      Post("/user/sylvain-user-id/delete", HttpEntity(ContentTypes.`application/json`, fakeRequest))
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
      }
    }
  }

  Feature("follow user") {
    Scenario("unconnected user") {
      Post(s"/user/${sylvain.userId.value}/follow") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("try to follow unexistant user") {

      Post("/user/non-existant/follow")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    Scenario("try to follow a user without public profile") {

      Post(s"/user/${fakeUser.userId.value}/follow")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status shouldBe StatusCodes.Forbidden
      }
    }

    Scenario("try to follow a user already followed") {

      when(userService.getFollowedUsers(any[UserId]))
        .thenReturn(Future.successful(Seq(publicUser.userId)))

      Post(s"/user/${publicUser.userId.value}/follow")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    Scenario("successfully follow a user") {
      when(userService.getFollowedUsers(any[UserId]))
        .thenReturn(Future.successful(Seq.empty))

      Post(s"/user/${publicUser.userId.value}/follow")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  Feature("unfollow a user") {
    Scenario("unconnected user") {
      Post(s"/user/${sylvain.userId.value}/unfollow") ~> routes ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    Scenario("try to unfollow unexistant user") {
      Post("/user/non-existant/unfollow")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    Scenario("try to unfollow a user already unfollowed") {
      when(userService.getFollowedUsers(sylvain.userId))
        .thenReturn(Future.successful(Seq.empty))

      Post(s"/user/${publicUser.userId.value}/unfollow")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    Scenario("successfully unfollow a user") {

      when(userService.getFollowedUsers(any[UserId]))
        .thenReturn(Future.successful(Seq(publicUser.userId)))

      Post(s"/user/${publicUser.userId.value}/unfollow")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }
  }

  Feature("get reconnect info") {

    Scenario("userId not found") {
      when(userService.reconnectInfo(any[UserId])).thenReturn(Future.successful(None))

      Post("/user/userId/reconnect") ~> routes ~> check {
        status shouldBe StatusCodes.NotFound
      }
    }

    Scenario("reconnect info ok") {
      when(userService.reconnectInfo(any[UserId]))
        .thenReturn(
          Future.successful(
            Some(
              ReconnectInfo(
                reconnectToken = "reconnect-token",
                firstName = Some("firstname"),
                avatarUrl = None,
                hiddenMail = "a*******z@mail.com",
                connectionMode = Seq(ConnectionMode.Mail)
              )
            )
          )
        )

      Post("/user/userId/reconnect") ~> routes ~> check {
        status shouldBe StatusCodes.OK
      }
    }

  }

  Feature("upload avatar") {
    val maxUploadFileSize = 4242L
    when(storageConfiguration.maxFileSize).thenReturn(maxUploadFileSize)
    Scenario("unauthorized not connected") {
      Post("/user/ABCD/upload-avatar") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("forbidden citizen") {
      Post("/user/ABCD/upload-avatar")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("forbidden moderator") {
      Post("/user/ABCD/upload-avatar")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("incorrect file type") {
      val request: Multipart = Multipart.FormData(fields = Map(
        "data" -> HttpEntity
          .Strict(ContentTypes.`application/x-www-form-urlencoded`, ByteString("incorrect file type"))
      )
      )

      Post("/user/ABCD/upload-avatar", request)
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.BadRequest)
      }
    }

    Scenario("storage unavailable") {
      when(storageService.uploadFile(eqTo(FileType.Operation), any[String], any[String], any[FileContent]))
        .thenReturn(Future.failed(new Exception("swift client error")))
      val request: Multipart =
        Multipart.FormData(
          Multipart.FormData.BodyPart
            .Strict(
              "data",
              HttpEntity.Strict(ContentType(MediaTypes.`image/jpeg`), ByteString("image")),
              Map("filename" -> "image.jpeg")
            )
        )

      Post("/user/ABCD/upload-avatar", request)
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.InternalServerError)
      }
    }

    Scenario("file too large uploaded by admin") {
      when(storageService.uploadFile(eqTo(FileType.Avatar), any[String], any[String], any[FileContent]))
        .thenReturn(Future.successful("path/to/uploaded/image.jpeg"))

      def entityOfSize(size: Int): Multipart = Multipart.FormData(
        Multipart.FormData.BodyPart
          .Strict(
            "data",
            HttpEntity.Strict(ContentType(MediaTypes.`image/jpeg`), ByteString("0" * size)),
            Map("filename" -> "image.jpeg")
          )
      )
      Post("/user/sylvain-user-id/upload-avatar", entityOfSize(maxUploadFileSize.toInt + 1))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.PayloadTooLarge)
      }
    }

    Scenario("file successfully uploaded and returned by admin or related-user") {
      when(storageService.uploadUserAvatar(any[UserId], any[String], any[String], any[FileContent]))
        .thenReturn(Future.successful("path/to/uploaded/image.jpeg"))

      def entityOfSize(size: Int): Multipart = Multipart.FormData(
        Multipart.FormData.BodyPart
          .Strict(
            "data",
            HttpEntity.Strict(ContentType(MediaTypes.`image/jpeg`), ByteString("0" * size)),
            Map("filename" -> "image.jpeg")
          )
      )
      Post("/user/sylvain-user-id/upload-avatar", entityOfSize(10))
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)

        val path: UploadResponse = entityAs[UploadResponse]
        path.path shouldBe "path/to/uploaded/image.jpeg"
      }
      Post("/user/sylvain-user-id/upload-avatar", entityOfSize(10))
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)

        val path: UploadResponse = entityAs[UploadResponse]
        path.path shouldBe "path/to/uploaded/image.jpeg"
      }
    }
  }

  Feature("read user information") {

    Scenario("get user when not connected") {
      Get(s"/user/${sylvain.userId.value}") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("get user with the correct connected user") {
      when(userService.getUser(eqTo(sylvain.userId)))
        .thenReturn(Future.successful(Some(sylvain)))

      Get(s"/user/${sylvain.userId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[UserResponse]
        result.userId should be(sylvain.userId)
      }
    }

    Scenario("get user with an incorrect connected user") {

      Get(s"/user/${sylvain.userId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("get user with as admin") {
      when(userService.getUser(eqTo(sylvain.userId)))
        .thenReturn(Future.successful(Some(sylvain)))

      Get(s"/user/${sylvain.userId.value}")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[UserResponse]
        result.userId should be(sylvain.userId)
      }
    }

    Scenario("get user profile when not connected") {
      Get(s"/user/${sylvain.userId.value}/profile") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("get user profile with the correct connected user") {
      when(userService.getUser(eqTo(sylvain.userId)))
        .thenReturn(Future.successful(Some(sylvain)))

      Get(s"/user/${sylvain.userId.value}/profile")
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {
        status should be(StatusCodes.OK)
        val result = entityAs[UserProfileResponse]
        result.firstName should be(sylvain.firstName)
      }
    }

    Scenario("get user profile with the incorrect connected user") {

      Get(s"/user/${sylvain.userId.value}/profile")
        .withHeaders(Authorization(OAuth2BearerToken(moderatorToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("get user profile as admin") {
      when(userService.getUser(eqTo(sylvain.userId)))
        .thenReturn(Future.successful(Some(sylvain)))

      Get(s"/user/${sylvain.userId.value}/profile")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

  }

  Feature("update user profile") {
    Scenario("unauthentified profile modification") {
      Put(s"/user/${sylvain.userId.value}/profile") ~> routes ~> check {
        status should be(StatusCodes.Unauthorized)
      }
    }

    Scenario("profile modification with the wrong user") {
      Put(s"/user/${sylvain.userId.value}/profile")
        .withHeaders(Authorization(OAuth2BearerToken(adminToken))) ~> routes ~> check {
        status should be(StatusCodes.Forbidden)
      }
    }

    Scenario("profile modification with the correct user") {

      val entity = """{
        |  "firstName": "new-firstname",
        |  "lastName": "new-lastname",
        |  "dateOfBirth": "1970-01-01",
        |  "avatarUrl": "https://some-avatar.com",
        |  "profession": "new-profession",
        |  "description": "new-description",
        |  "postalCode": "12345",
        |  "optInNewsletter": true,
        |  "website": "https://some-website.com"
      }""".stripMargin

      Put(s"/user/${sylvain.userId.value}/profile")
        .withEntity(HttpEntity(ContentTypes.`application/json`, entity))
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {

        status should be(StatusCodes.OK)

        val profile = responseAs[UserProfileResponse]
        profile.firstName should contain("new-firstname")
        profile.lastName should contain("new-lastname")
        profile.dateOfBirth should contain(LocalDate.parse("1970-01-01"))
        profile.avatarUrl should contain("https://some-avatar.com")
        profile.profession should contain("new-profession")
        profile.description should contain("new-description")
        profile.postalCode should contain("12345")
        profile.optInNewsletter should be(true)
        profile.website should contain("https://some-website.com")

      }
    }

    Scenario("bad request") {

      val invalidEntity = """{
                     |  "lastName": "new-lastname"
      }""".stripMargin

      Put(s"/user/${sylvain.userId.value}/profile")
        .withEntity(HttpEntity(ContentTypes.`application/json`, invalidEntity))
        .withHeaders(Authorization(OAuth2BearerToken(citizenToken))) ~> routes ~> check {

        status should be(StatusCodes.BadRequest)

      }
    }
  }
}

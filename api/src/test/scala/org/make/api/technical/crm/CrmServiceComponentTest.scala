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

package org.make.api.technical.crm

import java.io.{BufferedReader, InputStreamReader}
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.query.scaladsl.{CurrentEventsByPersistenceIdQuery, CurrentPersistenceIdsQuery, ReadJournal}
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.scaladsl
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import org.make.api.extensions.{MailJetConfiguration, MailJetConfigurationComponent}
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.proposal.{
  ProposalCoordinatorService,
  ProposalCoordinatorServiceComponent,
  ProposalSearchEngine,
  ProposalSearchEngineComponent
}
import org.make.api.question.{QuestionService, QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.technical.ReadJournalComponent
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.user.{
  PersistentUserToAnonymizeService,
  PersistentUserToAnonymizeServiceComponent,
  UserService,
  UserServiceComponent
}
import org.make.api.userhistory._
import org.make.api.{ActorSystemComponent, MakeUnitTest, StaminaTestUtils}
import org.make.core.history.HistoryActions.Trusted
import org.make.core.operation._
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito.{never, verify, when}
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.PrivateMethodTester
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

class CrmServiceComponentTest
    extends MakeUnitTest
    with DefaultCrmServiceComponent
    with OperationServiceComponent
    with QuestionServiceComponent
    with MailJetConfigurationComponent
    with ErrorAccumulatingCirceSupport
    with ActorSystemComponent
    with UserHistoryCoordinatorServiceComponent
    with UserServiceComponent
    with ReadJournalComponent
    with ProposalCoordinatorServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with CrmClientComponent
    with PersistentCrmUserServiceComponent
    with ProposalSearchEngineComponent
    with PrivateMethodTester {

  trait MakeReadJournalForMocks
      extends ReadJournal
      with CurrentPersistenceIdsQuery
      with CurrentEventsByPersistenceIdQuery

  override lazy val actorSystem: ActorSystem = CrmServiceComponentTest.actorSystem
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val proposalJournal: MakeReadJournal = mock[MakeReadJournalForMocks]
  override val userJournal: MakeReadJournal = mock[MakeReadJournalForMocks]
  override val sessionJournal: MakeReadJournal = mock[MakeReadJournalForMocks]
  override val mailJetConfiguration: MailJetConfiguration = mock[MailJetConfiguration]
  override val operationService: OperationService = mock[OperationService]
  override val questionService: QuestionService = mock[QuestionService]
  override val userService: UserService = mock[UserService]
  override val persistentUserToAnonymizeService: PersistentUserToAnonymizeService =
    mock[PersistentUserToAnonymizeService]

  override val crmClient: CrmClient = mock[CrmClient]
  override val persistentCrmUserService: PersistentCrmUserService = mock[PersistentCrmUserService]
  override val proposalCoordinatorService: ProposalCoordinatorService = mock[ProposalCoordinatorService]
  override val elasticsearchProposalAPI: ProposalSearchEngine = mock[ProposalSearchEngine]

  when(mailJetConfiguration.userListBatchSize).thenReturn(1000)

  val zonedDateTimeInThePast: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")
  val zonedDateTimeInThePastAt31daysBefore: ZonedDateTime = DateHelper.now().minusDays(31)
  val zonedDateTimeNow: ZonedDateTime = DateHelper.now()

  def persistentCrmUser(id: String): PersistentCrmUser = {
    PersistentCrmUser(
      userId = id,
      email = s"$id@make.org",
      fullName = "Lapin",
      firstname = "Roger",
      zipcode = None,
      dateOfBirth = None,
      emailValidationStatus = true,
      emailHardbounceStatus = false,
      unsubscribeStatus = false,
      accountCreationDate = None,
      accountCreationSource = None,
      accountCreationOrigin = None,
      accountCreationOperation = None,
      accountCreationCountry = None,
      countriesActivity = None,
      lastCountryActivity = None,
      lastLanguageActivity = None,
      totalNumberProposals = None,
      totalNumberVotes = None,
      firstContributionDate = None,
      lastContributionDate = None,
      operationActivity = None,
      sourceActivity = None,
      activeCore = None,
      daysOfActivity = None,
      daysOfActivity30d = None,
      userType = None
    )
  }

  val user = User(
    userId = UserId("50b3d4f6-4bfe-4102-94b1-bfcfdf12ef74"),
    email = "alex.terrieur@gmail.com",
    firstName = Some("Alex"),
    lastName = Some("Terrieur"),
    lastIp = None,
    hashedPassword = None,
    enabled = true,
    emailVerified = true,
    lastConnection = DateHelper.now(),
    verificationToken = None,
    verificationTokenExpiresAt = None,
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq.empty,
    country = Country("FR"),
    language = Language("fr"),
    profile = Some(Profile.default.copy(optInNewsletter = true)),
    createdAt = None,
    updatedAt = None,
    lastMailingError = None,
    organisationName = None,
    availableQuestions = Seq.empty
  )

  def readEvents(resource: String): Source[EventEnvelope, NotUsed] = {
    val file = getClass.getClassLoader.getResourceAsStream(resource)
    val reader = new BufferedReader(new InputStreamReader(file))
    val events = reader
      .lines()
      .map[Object] { line =>
        val splitted = line.split(":", 3)
        StaminaTestUtils.deserializeEventFromJson[Object](splitted(0), splitted(2), splitted(1).toInt)
      }
      .toArray

    Source(events.map(EventEnvelope(Offset.noOffset, user.userId.value, 0L, _)).toVector)
  }

  when(mailJetConfiguration.url).thenReturn("http://localhost:1234")
  when(mailJetConfiguration.userListBatchSize).thenReturn(1000)
  when(mailJetConfiguration.httpBufferSize).thenReturn(200)
  when(mailJetConfiguration.campaignApiKey).thenReturn("api-key")
  when(mailJetConfiguration.campaignSecretKey).thenReturn("secret-key")

  when(persistentUserToAnonymizeService.removeAllByEmails(any[Seq[String]]))
    .thenAnswer(invocation => Future.successful(invocation.getArgument[Seq[String]](0).size))

  val questionFr = Question(
    questionId = QuestionId("question-fr"),
    slug = "question-fr",
    country = Country("FR"),
    language = Language("fr"),
    question = "question ?",
    operationId = Some(OperationId("999-99-99")),
    themeId = None
  )

  val questionGb = Question(
    questionId = QuestionId("question-gb"),
    slug = "question-gb",
    country = Country("GB"),
    language = Language("en"),
    question = "question ?",
    operationId = Some(OperationId("888-88-88")),
    themeId = None
  )

  val questionIt = Question(
    questionId = QuestionId("question-it"),
    slug = "question-it",
    country = Country("IT"),
    language = Language("it"),
    question = "question ?",
    operationId = Some(OperationId("777-77-77")),
    themeId = None
  )

  val proposalFr: Proposal = Proposal(
    proposalId = ProposalId("proposalId-fr"),
    slug = "slug",
    content = "content",
    author = UserId("author"),
    labels = Seq.empty,
    theme = None,
    status = ProposalStatus.Accepted,
    creationContext = RequestContext.empty,
    createdAt = None,
    updatedAt = None,
    events = Nil,
    votes = Seq.empty,
    questionId = Some(questionFr.questionId)
  )
  val proposalGb: Proposal =
    proposalFr.copy(proposalId = ProposalId("proposalId-gb"), questionId = Some(questionGb.questionId))
  val proposalIt: Proposal =
    proposalFr.copy(proposalId = ProposalId("proposalId-it"), questionId = Some(questionIt.questionId))

  when(proposalCoordinatorService.getProposal(ProposalId("proposalId-fr")))
    .thenReturn(Future.successful(Some(proposalFr)))
  when(proposalCoordinatorService.getProposal(ProposalId("proposalId-gb")))
    .thenReturn(Future.successful(Some(proposalGb)))
  when(proposalCoordinatorService.getProposal(ProposalId("proposalId-it")))
    .thenReturn(Future.successful(Some(proposalIt)))

  val defaultOperation: Operation = Operation(
    status = OperationStatus.Active,
    operationId = OperationId("default"),
    slug = "default",
    defaultLanguage = Language("fr"),
    allowedSources = Seq.empty,
    operationKind = OperationKind.PublicConsultation,
    events = List.empty,
    createdAt = None,
    updatedAt = None,
    questions = Seq.empty
  )

  val operations: Seq[Operation] = Seq(
    defaultOperation.copy(operationId = OperationId("345-34-89"), slug = "culture"),
    defaultOperation.copy(operationId = OperationId("999-99-99"), slug = "chance"),
    defaultOperation.copy(operationId = OperationId("200-20-11"), slug = "vff")
  )

  when(operationService.find(slug = None, country = None, maybeSource = None, openAt = None))
    .thenReturn(Future.successful(operations))

  val fooProfile = Profile(
    dateOfBirth = Some(LocalDate.parse("2000-01-01")),
    avatarUrl = Some("https://www.example.com"),
    profession = Some("profession"),
    phoneNumber = Some("010101"),
    description = Some("Resume of who I am"),
    twitterId = Some("@twitterid"),
    facebookId = Some("facebookid"),
    googleId = Some("googleId"),
    gender = Some(Gender.Male),
    genderName = Some("other"),
    postalCode = Some("93"),
    karmaLevel = Some(2),
    locale = Some("fr_FR"),
    socioProfessionalCategory = Some(SocioProfessionalCategory.Farmers)
  )

  val fooUser = User(
    userId = UserId("1"),
    email = "foo@example.com",
    firstName = Some("Foo"),
    lastName = Some("John"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    enabled = true,
    emailVerified = true,
    lastConnection = zonedDateTimeInThePast,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(zonedDateTimeInThePast),
    resetToken = None,
    resetTokenExpiresAt = None,
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    country = Country("FR"),
    language = Language("fr"),
    profile = Some(fooProfile),
    createdAt = Some(zonedDateTimeInThePast),
    availableQuestions = Seq.empty
  )

  val registerCitizenEventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "foo-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogRegisterCitizenEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("core"),
        operationId = Some(OperationId("999-99-99")),
        country = Some(Country("FR")),
        language = Some(Language("fr"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePast,
        actionType = LogRegisterCitizenEvent.action,
        arguments = UserRegistered(
          email = "me@make.org",
          dateOfBirth = Some(LocalDate.parse("1970-01-01")),
          firstName = Some("me"),
          lastName = Some("myself"),
          profession = Some("doer"),
          postalCode = Some("75011")
        )
      )
    )
  )

  val userProposalEventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserProposalEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("core"),
        operationId = Some(OperationId("vff")),
        country = Some(Country("IT")),
        language = Some(Language("it"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePast,
        actionType = LogUserProposalEvent.action,
        arguments = UserProposal(content = "il faut proposer", theme = Some(ThemeId("my-theme")))
      )
    )
  )

  val userVoteEventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserVoteEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("vff"),
        currentTheme = Some(ThemeId("foo-theme-id")),
        country = Some(Country("GB")),
        language = Some(Language("uk"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePast,
        actionType = ProposalVoteAction.name,
        arguments = UserVote(proposalId = ProposalId("proposalId-gb"), voteKey = VoteKey.Neutral, trust = Trusted)
      )
    )
  )

  val userVoteEventEnvelope2 = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserVoteEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("culture"),
        operationId = Some(OperationId("culture")),
        country = Some(Country("FR")),
        language = Some(Language("fr"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePast,
        actionType = ProposalVoteAction.name,
        arguments = UserVote(proposalId = ProposalId("proposalId-fr"), voteKey = VoteKey.Agree, trust = Trusted)
      )
    )
  )

  val userVoteEventEnvelope3 = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserVoteEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("culture"),
        operationId = Some(OperationId("culture")),
        country = Some(Country("FR")),
        language = Some(Language("fr"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePastAt31daysBefore,
        actionType = ProposalVoteAction.name,
        arguments = UserVote(proposalId = ProposalId("proposalId-fr"), voteKey = VoteKey.Agree, trust = Trusted)
      )
    )
  )

  val userVoteEventEnvelope4 = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserVoteEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("culture"),
        operationId = Some(OperationId("invalidoperation")),
        country = Some(Country("FR")),
        language = Some(Language("fr"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePastAt31daysBefore.plusDays(2),
        actionType = ProposalVoteAction.name,
        arguments = UserVote(proposalId = ProposalId("proposalId-fr"), voteKey = VoteKey.Agree, trust = Trusted)
      )
    )
  )

  val userVoteEventEnvelope5 = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserVoteEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("culture"),
        operationId = Some(OperationId("200-20-11")),
        country = Some(Country("FR")),
        language = Some(Language("fr"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePastAt31daysBefore.plusDays(2),
        actionType = ProposalVoteAction.name,
        arguments = UserVote(proposalId = ProposalId("proposalId"), voteKey = VoteKey.Agree, trust = Trusted)
      )
    )
  )

  when(userJournal.currentEventsByPersistenceId(matches(fooUser.userId.value), any[Long], any[Long]))
    .thenReturn(
      scaladsl.Source(
        List(
          registerCitizenEventEnvelope,
          userProposalEventEnvelope,
          userVoteEventEnvelope,
          userVoteEventEnvelope2,
          userVoteEventEnvelope3,
          userVoteEventEnvelope4,
          userVoteEventEnvelope5
        )
      )
    )

  val userWithoutRegisteredEvent: User = fooUser.copy(userId = UserId("user-without-registered-event"))
  val userWithoutRegisteredEventAfterDateFix: User =
    fooUser.copy(userId = UserId("user-without-registered-event"), createdAt = Some(zonedDateTimeNow))

  when(userJournal.currentEventsByPersistenceId(matches(userWithoutRegisteredEvent.userId.value), any[Long], any[Long]))
    .thenReturn(scaladsl.Source(List.empty))

  feature("data normalization") {
    scenario("users properties are normalized when user has no register event") {
      Given("a registered user without register event")
      When("I get user properties")

      val futureProperties =
        crmService.getPropertiesFromUser(userWithoutRegisteredEvent, new QuestionResolver(Seq.empty, Map.empty))
      Then("data are normalized")
      whenReady(futureProperties, Timeout(3.seconds)) { maybeProperties =>
        maybeProperties.userId shouldBe Some(UserId("user-without-registered-event"))
        maybeProperties.firstName shouldBe Some("Foo")
        maybeProperties.postalCode shouldBe Some("93")
        maybeProperties.dateOfBirth shouldBe Some("2000-01-01T00:00:00Z")
        maybeProperties.emailValidationStatus shouldBe Some(true)
        maybeProperties.emailHardBounceValue shouldBe Some(false)
        maybeProperties.unsubscribeStatus shouldBe Some(false)
        maybeProperties.accountCreationDate shouldBe Some("2017-06-01T12:30:40Z")
        maybeProperties.accountCreationSource shouldBe Some("core")
        maybeProperties.accountCreationOrigin shouldBe None
        maybeProperties.accountCreationSlug shouldBe None
        maybeProperties.accountCreationCountry shouldBe Some("FR")
        maybeProperties.countriesActivity shouldBe Some("FR")
        maybeProperties.lastCountryActivity shouldBe Some("FR")
        maybeProperties.lastLanguageActivity shouldBe Some("fr")
        maybeProperties.totalProposals shouldBe Some(0)
        maybeProperties.totalVotes shouldBe Some(0)
        maybeProperties.firstContributionDate shouldBe None
        maybeProperties.lastContributionDate shouldBe None
        maybeProperties.operationActivity shouldBe Some("")
        maybeProperties.activeCore shouldBe None
        maybeProperties.daysOfActivity shouldBe Some(0)
        maybeProperties.daysOfActivity30 shouldBe Some(0)
        maybeProperties.userType shouldBe Some("B2C")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(maybeProperties.updatedAt.getOrElse(""))
        updatedAt.isBefore(DateHelper.now()) && updatedAt.isAfter(DateHelper.now().minusSeconds(10)) shouldBe true
      }
    }

    scenario("users properties are not normalized after date fix") {
      Given("a registered user without register event and with a recent creation date")
      When("I get user properties")

      val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
      val futureProperties = crmService.getPropertiesFromUser(
        userWithoutRegisteredEventAfterDateFix,
        new QuestionResolver(Seq.empty, Map.empty)
      )
      Then("data are not normalized")
      whenReady(futureProperties, Timeout(3.seconds)) { maybeProperties =>
        maybeProperties.userId shouldBe Some(UserId("user-without-registered-event"))
        maybeProperties.firstName shouldBe Some("Foo")
        maybeProperties.postalCode shouldBe Some("93")
        maybeProperties.dateOfBirth shouldBe Some("2000-01-01T00:00:00Z")
        maybeProperties.emailValidationStatus shouldBe Some(true)
        maybeProperties.emailHardBounceValue shouldBe Some(false)
        maybeProperties.unsubscribeStatus shouldBe Some(false)
        maybeProperties.accountCreationDate shouldBe Some(zonedDateTimeNow.format(dateFormatter))
        maybeProperties.accountCreationSource shouldBe None
        maybeProperties.accountCreationOrigin shouldBe None
        maybeProperties.accountCreationSlug shouldBe None
        maybeProperties.accountCreationCountry shouldBe Some("FR")
        maybeProperties.countriesActivity shouldBe Some("FR")
        maybeProperties.lastCountryActivity shouldBe Some("FR")
        maybeProperties.lastLanguageActivity shouldBe Some("fr")
        maybeProperties.totalProposals shouldBe Some(0)
        maybeProperties.totalVotes shouldBe Some(0)
        maybeProperties.firstContributionDate shouldBe None
        maybeProperties.lastContributionDate shouldBe None
        maybeProperties.operationActivity shouldBe Some("")
        maybeProperties.activeCore shouldBe None
        maybeProperties.daysOfActivity shouldBe Some(0)
        maybeProperties.daysOfActivity30 shouldBe Some(0)
        maybeProperties.userType shouldBe Some("B2C")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(maybeProperties.updatedAt.getOrElse(""))
        updatedAt.isBefore(DateHelper.now()) && updatedAt.isAfter(DateHelper.now().minusSeconds(10)) shouldBe true
      }
    }
  }

  feature("add user to OptInList") {
    scenario("get properties from user and his events") {
      Given("a registred user")

      When("I add a user into optInList")

      when(questionService.searchQuestion(any[SearchQuestionRequest])).thenReturn(
        Future.successful(
          Seq(
            Question(
              QuestionId("question-id"),
              "question",
              Country("FR"),
              Language("fr"),
              "question ?",
              Some(OperationId("999-99-99")),
              None
            )
          )
        )
      )

      Then("The properties are calculated from UserHistory")

      val questions = Seq(
        Question(
          questionId = QuestionId("vff-fr"),
          slug = "vff-fr",
          country = Country("FR"),
          language = Language("fr"),
          question = "200-20-11",
          operationId = Some(OperationId("200-20-11")),
          None
        ),
        Question(
          questionId = QuestionId("culture"),
          slug = "culture",
          country = Country("FR"),
          language = Language("fr"),
          question = "345-34-89",
          operationId = Some(OperationId("345-34-89")),
          None
        ),
        Question(
          questionId = QuestionId("chance-aux-jeunes"),
          slug = "chance-aux-jeunes",
          country = Country("FR"),
          language = Language("fr"),
          question = "999-99-99",
          operationId = Some(OperationId("999-99-99")),
          None
        )
      )

      when(
        elasticsearchProposalAPI
          .countProposals(
            SearchQuery(
              filters = Some(
                SearchFilters(
                  user = Some(UserSearchFilter(fooUser.userId)),
                  status = Some(StatusSearchFilter(ProposalStatus.statusMap.values.toSeq))
                )
              )
            )
          )
      ).thenReturn(Future.successful(0L))

      val futureProperties = crmService.getPropertiesFromUser(
        fooUser,
        new QuestionResolver(questions, operations.map(operation => operation.slug -> operation.operationId).toMap)
      )
      whenReady(futureProperties, Timeout(3.seconds)) { maybeProperties =>
        maybeProperties.userId shouldBe Some(UserId("1"))
        maybeProperties.firstName shouldBe Some("Foo")
        maybeProperties.postalCode shouldBe Some("93")
        maybeProperties.dateOfBirth shouldBe Some("2000-01-01T00:00:00Z")
        maybeProperties.emailValidationStatus shouldBe Some(true)
        maybeProperties.emailHardBounceValue shouldBe Some(false)
        maybeProperties.unsubscribeStatus shouldBe Some(false)
        maybeProperties.accountCreationDate shouldBe Some("2017-06-01T12:30:40Z")
        maybeProperties.accountCreationSource shouldBe Some("core")
        maybeProperties.accountCreationOrigin shouldBe None
        maybeProperties.accountCreationSlug shouldBe Some("chance-aux-jeunes")
        maybeProperties.accountCreationCountry shouldBe Some("FR")
        maybeProperties.countriesActivity shouldBe Some("FR,IT,GB")
        maybeProperties.lastCountryActivity shouldBe Some("FR")
        maybeProperties.lastLanguageActivity shouldBe Some("fr")
        maybeProperties.totalProposals shouldBe Some(1)
        maybeProperties.totalVotes shouldBe Some(5)
        maybeProperties.firstContributionDate shouldBe Some("2017-06-01T12:30:40Z")
        maybeProperties.lastContributionDate shouldBe Some(
          zonedDateTimeInThePastAt31daysBefore
            .plusDays(2)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC))
        )
        maybeProperties.operationActivity.toSeq.flatMap(_.split(",").toSeq).sorted shouldBe Seq(
          "chance-aux-jeunes",
          "culture",
          "vff-fr"
        )
        maybeProperties.activeCore shouldBe Some(true)
        maybeProperties.daysOfActivity shouldBe Some(3)
        maybeProperties.daysOfActivity30 shouldBe Some(1)
        maybeProperties.userType shouldBe Some("B2C")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(maybeProperties.updatedAt.getOrElse(""))
        updatedAt.isBefore(DateHelper.now()) && updatedAt.isAfter(DateHelper.now().minusSeconds(10)) shouldBe true
      }
    }
  }

  feature("anonymize") {
    val emails: Seq[String] = Seq("toto@tata.com", "tata@tata.com", "titi@tata.com")

    scenario("all users anonymized") {
      when(crmClient.deleteContactByEmail(ArgumentMatchers.eq("toto@tata.com"))(ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(crmClient.deleteContactByEmail(ArgumentMatchers.eq("tata@tata.com"))(ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(crmClient.deleteContactByEmail(ArgumentMatchers.eq("titi@tata.com"))(ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(true))

      val futureRemovedEmails: Future[Unit] = crmService.deleteAnonymizedContacts(emails)

      whenReady(futureRemovedEmails, Timeout(3.seconds)) { _ =>
        verify(persistentUserToAnonymizeService)
          .removeAllByEmails(Seq("toto@tata.com", "tata@tata.com", "titi@tata.com"))
      }
    }

    scenario("one users not found in mailjet") {
      when(crmClient.deleteContactByEmail(ArgumentMatchers.eq("toto@tata.com"))(ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(crmClient.deleteContactByEmail(ArgumentMatchers.eq("tata@tata.com"))(ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(crmClient.deleteContactByEmail(ArgumentMatchers.eq("titi@tata.com"))(ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(false))

      val futureRemovedEmails: Future[Unit] = crmService.deleteAnonymizedContacts(emails)

      whenReady(futureRemovedEmails, Timeout(3.seconds)) { _ =>
        verify(persistentUserToAnonymizeService)
          .removeAllByEmails(Seq("toto@tata.com", "tata@tata.com"))
      }
    }

    scenario("zero users anonymized") {
      when(crmClient.deleteContactByEmail(ArgumentMatchers.eq("toto@tata.com"))(ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(false))
      when(crmClient.deleteContactByEmail(ArgumentMatchers.eq("tata@tata.com"))(ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(false))
      when(crmClient.deleteContactByEmail(ArgumentMatchers.eq("titi@tata.com"))(ArgumentMatchers.any[ExecutionContext]))
        .thenReturn(Future.successful(false))

      Mockito.clearInvocations(persistentUserToAnonymizeService)

      val futureRemovedEmails: Future[Unit] = crmService.deleteAnonymizedContacts(emails)

      whenReady(futureRemovedEmails, Timeout(3.seconds)) { _ =>
        verify(persistentUserToAnonymizeService, never()).removeAllByEmails(any[Seq[String]])
      }
    }

    scenario("invalid email") {

      val futureRemovedEmails: Future[Unit] = crmService.deleteAnonymizedContacts(Seq("invalid"))

      whenReady(futureRemovedEmails, Timeout(3.seconds)) { _ =>
        verify(persistentUserToAnonymizeService)
          .removeAllByEmails(Seq("invalid"))
      }
    }
  }

  feature("synchronizing list") {
    scenario("no user to synchronize") {
      when(
        persistentCrmUserService.list(unsubscribed = Some(false), hardBounced = false, page = 0, numberPerPage = 1000)
      ).thenReturn(Future.successful(Seq.empty))

      whenReady(crmService.synchronizeList(DateHelper.now().toString, CrmList.OptIn), Timeout(2.seconds)) { _ =>
        // Synchro should somewhat end
      }
    }

    scenario("multiple users") {
      when(persistentCrmUserService.list(matches(Some(true)), matches(false), any[Int], any[Int]))
        .thenReturn(
          Future.successful(Seq(persistentCrmUser("1"), persistentCrmUser("2"))),
          Future.successful(Seq(persistentCrmUser("3"), persistentCrmUser("4"))),
          Future.successful(Seq(persistentCrmUser("5"), persistentCrmUser("6"))),
          Future.successful(Seq(persistentCrmUser("7"), persistentCrmUser("8"))),
          Future.successful(Seq(persistentCrmUser("9"), persistentCrmUser("10"))),
          Future.successful(Seq.empty)
        )

      when(crmClient.manageContactList(any[ManageManyContacts])(any[ExecutionContext])).thenReturn(
        Future.successful(BasicCrmResponse(1, 1, Seq(JobId(1L)))),
        Future.successful(BasicCrmResponse(1, 1, Seq(JobId(2L)))),
        Future.successful(BasicCrmResponse(1, 1, Seq(JobId(3L)))),
        Future.successful(BasicCrmResponse(1, 1, Seq(JobId(4L)))),
        Future.successful(BasicCrmResponse(1, 1, Seq(JobId(5L))))
      )

      when(crmClient.manageContactListJobDetails(matches("1"))(any[ExecutionContext]))
        .thenReturn(
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Completed"))))
        )

      when(crmClient.manageContactListJobDetails(matches("2"))(any[ExecutionContext]))
        .thenReturn(
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Completed"))))
        )

      when(crmClient.manageContactListJobDetails(matches("3"))(any[ExecutionContext]))
        .thenReturn(
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Completed"))))
        )

      when(crmClient.manageContactListJobDetails(matches("4"))(any[ExecutionContext]))
        .thenReturn(
          Future.failed(new IllegalStateException("Don't worry, this exception is fake")),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Completed"))))
        )

      when(crmClient.manageContactListJobDetails(matches("5"))(any[ExecutionContext]))
        .thenReturn(
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Pending")))),
          Future.successful(BasicCrmResponse(1, 1, Seq(JobDetailsResponse(Seq.empty, 0, "", "", "", "", "Error"))))
        )

      whenReady(crmService.synchronizeList(DateHelper.now().toString, CrmList.OptOut), Timeout(60.seconds)) { _ =>
        Mockito
          .verify(crmClient, Mockito.times(25))
          .manageContactListJobDetails(any[String])(any[ExecutionContext])
      }
    }
  }

  feature("read events") {
    scenario("user 50b3d4f6-4bfe-4102-94b1-bfcfdf12ef74") {
      val source = readEvents("events/user-50b3d4f6-4bfe-4102-94b1-bfcfdf12ef74")

      val operationId = OperationId("a818ef52-cd54-4aa7-bd3d-67e7bf4c4ea5")

      val question = Question(
        QuestionId("7d2ba29b-d503-44b8-98a0-5b9ae8b8bc69"),
        "my-question",
        Country("FR"),
        Language("fr"),
        "Comment sauver le monde ?",
        Some(operationId),
        None
      )
      val resolver = new QuestionResolver(Seq(question), Map())

      when(userJournal.currentEventsByPersistenceId(matches(user.userId.value), any[Long], any[Long]))
        .thenReturn(source)

      whenReady(crmService.getPropertiesFromUser(user, resolver), Timeout(5.seconds)) { result =>
        result.operationActivity should contain(question.slug)
        val persistentUser = PersistentCrmUser.fromContactProperty(user.email, user.fullName.get, result)
        persistentUser.operationActivity should contain(question.slug)
        persistentUser.totalNumberProposals should contain(2)
      }
    }
    scenario("user 8c0dcb2a-d4f8-4514-b1f1-8077ba314594") {
      val source = readEvents("events/user-8c0dcb2a-d4f8-4514-b1f1-8077ba314594")

      val questionId1 = QuestionId("question-1")
      val questionId2 = QuestionId("question-2")

      val proposal1Date = ZonedDateTime.parse("2019-03-18T23:00:31.243Z")
      val proposal2Date = ZonedDateTime.parse("2019-03-18T23:21:03.501Z")

      val proposals = Seq(
        IndexedProposal(
          id = ProposalId("proposal-1"),
          userId = UserId("8c0dcb2a-d4f8-4514-b1f1-8077ba314594"),
          content = "proposal 1",
          slug = "proposal-1",
          status = Accepted,
          createdAt = proposal1Date,
          updatedAt = None,
          votes = Seq.empty,
          votesCount = 42,
          votesVerifiedCount = 42,
          toEnrich = true,
          scores = IndexedScores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
          context = None,
          trending = None,
          labels = Seq.empty,
          author = Author(None, None, None, None, None, None),
          organisations = Seq.empty,
          country = Country("FR"),
          language = Language("fr"),
          themeId = None,
          question = Some(IndexedProposalQuestion(questionId1, "question-1", "", "", None, None, isOpen = true)),
          tags = Seq.empty,
          ideaId = None,
          operationId = Some(OperationId("")),
          sequencePool = SequencePool.New,
          initialProposal = false,
          refusalReason = None,
          operationKind = None
        ),
        IndexedProposal(
          id = ProposalId("proposal-2"),
          userId = UserId("8c0dcb2a-d4f8-4514-b1f1-8077ba314594"),
          content = "proposal 2",
          slug = "proposal-2",
          status = Accepted,
          createdAt = proposal2Date,
          updatedAt = None,
          votes = Seq.empty,
          votesCount = 42,
          votesVerifiedCount = 42,
          toEnrich = true,
          scores = IndexedScores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
          context = None,
          trending = None,
          labels = Seq.empty,
          author = Author(None, None, None, None, None, None),
          organisations = Seq.empty,
          country = Country("FR"),
          language = Language("fr"),
          themeId = None,
          question = Some(IndexedProposalQuestion(questionId2, "question-2", "", "", None, None, isOpen = true)),
          tags = Seq.empty,
          ideaId = None,
          operationId = Some(OperationId("")),
          sequencePool = SequencePool.New,
          initialProposal = false,
          refusalReason = None,
          operationKind = None
        )
      )

      when(
        elasticsearchProposalAPI
          .searchProposals(
            SearchQuery(
              filters = Some(
                SearchFilters(
                  user = Some(UserSearchFilter(UserId("8c0dcb2a-d4f8-4514-b1f1-8077ba314594"))),
                  status = Some(StatusSearchFilter(ProposalStatus.statusMap.values.toSeq))
                )
              ),
              limit = Some(2)
            )
          )
      ).thenReturn(Future.successful(ProposalsSearchResult(2, proposals)))

      when(
        elasticsearchProposalAPI
          .countProposals(
            SearchQuery(
              filters = Some(
                SearchFilters(
                  user = Some(UserSearchFilter(UserId("8c0dcb2a-d4f8-4514-b1f1-8077ba314594"))),
                  status = Some(StatusSearchFilter(ProposalStatus.statusMap.values.toSeq))
                )
              )
            )
          )
      ).thenReturn(Future.successful(2L))

      val operationId1 = OperationId("a818ef52-cd54-4aa7-bd3d-67e7bf4c4ea5")

      val questions =
        Seq(
          Question(
            questionId1,
            "question-1",
            Country("FR"),
            Language("fr"),
            "Comment sauver le monde ?",
            Some(operationId1),
            None
          ),
          Question(
            questionId2,
            "question-2",
            Country("FR"),
            Language("fr"),
            "Comment resauver le monde ?",
            Some(OperationId("who cares?")),
            None
          )
        )

      val resolver = new QuestionResolver(questions, Map())

      when(userJournal.currentEventsByPersistenceId(matches(user.userId.value), any[Long], any[Long]))
        .thenReturn(source)

      whenReady(crmService.getPropertiesFromUser(user, resolver), Timeout(5.seconds)) { result =>
        result.operationActivity.toSeq.flatMap(_.split(",").sorted) should be(Seq("question-1", "question-2"))
        result.totalProposals should contain(2)
        verify(elasticsearchProposalAPI)
          .searchProposals(
            SearchQuery(
              filters = Some(
                SearchFilters(
                  user = Some(UserSearchFilter(UserId("8c0dcb2a-d4f8-4514-b1f1-8077ba314594"))),
                  status = Some(StatusSearchFilter(ProposalStatus.statusMap.values.toSeq))
                )
              ),
              limit = Some(2)
            )
          )
      }
    }
  }

  feature("createCrmUsers") {
    scenario("single user") {

      val source = readEvents("events/user-50b3d4f6-4bfe-4102-94b1-bfcfdf12ef74")

      val operationId = OperationId("a818ef52-cd54-4aa7-bd3d-67e7bf4c4ea5")

      val question = Question(
        QuestionId("7d2ba29b-d503-44b8-98a0-5b9ae8b8bc69"),
        "my-question",
        Country("FR"),
        Language("fr"),
        "Comment sauver le monde ?",
        Some(operationId),
        None
      )

      when(operationService.findSimple()).thenReturn(
        Future.successful(
          Seq(
            SimpleOperation(
              operationId,
              OperationStatus.Pending,
              question.slug,
              Seq("core"),
              Language("fr"),
              OperationKind.PrivateConsultation,
              None,
              None
            )
          )
        )
      )

      when(questionService.searchQuestion(SearchQuestionRequest())).thenReturn(Future.successful(Seq(question)))

      when(userJournal.currentEventsByPersistenceId(matches(user.userId.value), any[Long], any[Long]))
        .thenReturn(source)

      when(persistentCrmUserService.truncateCrmUsers()).thenReturn(Future.successful {})

      when(userService.findUsersForCrmSynchro(None, None, 0, mailJetConfiguration.userListBatchSize))
        .thenReturn(Future.successful(Seq(user)))

      when(userService.findUsersForCrmSynchro(None, None, 1, mailJetConfiguration.userListBatchSize))
        .thenReturn(Future.successful(Seq()))

      when(persistentCrmUserService.persist(any[Seq[PersistentCrmUser]])).thenAnswer { invocation =>
        val users = invocation.getArgument[Seq[PersistentCrmUser]](0)
        if (users.forall(_.operationActivity.contains(question.slug))) {
          Future.successful(users)
        } else {
          fail()
        }
      }

      whenReady(crmService.createCrmUsers(), Timeout(2.seconds)) { _ =>
        verify(persistentCrmUserService).persist(any[Seq[PersistentCrmUser]])
      }

    }
  }

  feature("question resolver") {
    scenario("empty resolver") {
      val resolver = new QuestionResolver(Seq.empty, Map.empty)
      resolver.extractQuestionWithOperationFromRequestContext(RequestContext.empty) should be(None)
      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(questionId = Some(QuestionId("my-question")))
      ) should be(None)
      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(
          operationId = Some(OperationId("my-operation")),
          country = Some(Country("Fr")),
          language = Some(Language("fr"))
        )
      ) should be(None)
    }

    scenario("get by id") {
      val questionId = QuestionId("my-question")
      val question =
        Question(questionId, "my-question", Country("FR"), Language("fr"), "?", Some(OperationId("toto")), None)
      val resolver = new QuestionResolver(Seq(question), Map.empty)

      val result = resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(questionId = Some(questionId))
      )

      result should contain(question)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(questionId = Some(QuestionId("unknown")))
      ) should be(None)
    }

    scenario("get by operation") {
      val questionId = QuestionId("my-question")
      val question =
        Question(questionId, "my-question", Country("FR"), Language("fr"), "?", Some(OperationId("my-operation")), None)
      val resolver = new QuestionResolver(Seq(question), Map.empty)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(operationId = Some(OperationId("my-operation")), country = None, language = None)
      ) should contain(question)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(
          operationId = Some(OperationId("my-operation")),
          country = Some(Country("FR")),
          language = Some(Language("fr"))
        )
      ) should contain(question)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(
          operationId = Some(OperationId("my-operation")),
          country = Some(Country("GB")),
          language = Some(Language("fr"))
        )
      ) should be(None)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(
          operationId = Some(OperationId("my-operation")),
          country = Some(Country("FR")),
          language = Some(Language("en"))
        )
      ) should be(None)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(operationId = Some(OperationId("unknown")))
      ) should be(None)
    }
    scenario("get by operation slug") {
      val questionId = QuestionId("my-question")
      val question =
        Question(questionId, "my-question", Country("FR"), Language("fr"), "?", Some(OperationId("my-operation")), None)
      val resolver = new QuestionResolver(Seq(question), Map("my-operation-slug" -> OperationId("my-operation")))

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(operationId = Some(OperationId("my-operation-slug")), country = None, language = None)
      ) should contain(question)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(
          operationId = Some(OperationId("my-operation-slug")),
          country = Some(Country("FR")),
          language = Some(Language("fr"))
        )
      ) should contain(question)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(
          operationId = Some(OperationId("my-operation-slug")),
          country = Some(Country("GB")),
          language = Some(Language("fr"))
        )
      ) should be(None)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(
          operationId = Some(OperationId("my-operation-slug")),
          country = Some(Country("FR")),
          language = Some(Language("en"))
        )
      ) should be(None)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(operationId = Some(OperationId("unknown")))
      ) should be(None)

    }
  }
}

object CrmServiceComponentTest {
  val configuration: String = "akka.log-dead-letters-during-shutdown = off"
  val actorSystem = ActorSystem("CrmServiceComponentTest", ConfigFactory.parseString(configuration))
}

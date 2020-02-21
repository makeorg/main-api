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
import java.nio.file.Path
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
import org.make.api.technical.{EventBusService, EventBusServiceComponent, ReadJournalComponent}
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.user.{
  PersistentUserToAnonymizeService,
  PersistentUserToAnonymizeServiceComponent,
  UserService,
  UserServiceComponent
}
import org.make.api.userhistory._
import org.make.api.{ActorSystemComponent, MakeUnitTest, StaminaTestUtils, TestUtils}
import org.make.core.history.HistoryActions.Trusted
import org.make.core.operation._
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal._
import org.make.core.proposal.indexed._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.user.{Role, User, UserId, UserType}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.{ArgumentMatchers, Mockito}
import org.mockito.verification.After
import org.scalatest.PrivateMethodTester
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io

import arbitraries._

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
    with EventBusServiceComponent
    with ScalaCheckDrivenPropertyChecks
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
  override val eventBusService: EventBusService = mock[EventBusService]

  when(mailJetConfiguration.userListBatchSize).thenReturn(1000)
  when(mailJetConfiguration.csvDirectory).thenReturn("/tmp/make/crm")
  when(mailJetConfiguration.tickInterval).thenReturn(5.milliseconds)
  when(mailJetConfiguration.delayBeforeResend).thenReturn(10.milliseconds)

  val zonedDateTimeInThePast: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")
  val zonedDateTimeInThePastAt31daysBefore: ZonedDateTime = DateHelper.now().minusDays(31)
  val zonedDateTimeNow: ZonedDateTime = DateHelper.now()

  override def beforeAll(): Unit = {
    super.beforeAll()
    Await.result(crmService.initializeDirectories(), 5.seconds)
  }

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
      userType = None,
      accountType = None
    )
  }

  val user = TestUtils.user(
    id = UserId("50b3d4f6-4bfe-4102-94b1-bfcfdf12ef74"),
    email = "alex.terrieur@gmail.com",
    firstName = Some("Alex"),
    lastName = Some("Terrieur"),
    profile = Profile.parseProfile(optInNewsletter = true)
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
      .toIndexedSeq

    Source(events.map(EventEnvelope(Offset.noOffset, user.userId.value, 0L, _)))
  }

  when(mailJetConfiguration.url).thenReturn("http://localhost:1234")
  when(mailJetConfiguration.userListBatchSize).thenReturn(1000)
  when(mailJetConfiguration.httpBufferSize).thenReturn(200)
  when(mailJetConfiguration.campaignApiKey).thenReturn("api-key")
  when(mailJetConfiguration.campaignSecretKey).thenReturn("secret-key")
  when(mailJetConfiguration.csvSize).thenReturn(300)
  when(mailJetConfiguration.csvDirectory).thenReturn("/tmp/make/crm")

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
    socioProfessionalCategory = Some(SocioProfessionalCategory.Farmers),
    website = Some("http://example.com"),
    politicalParty = Some("PP")
  )

  val fooUser = TestUtils.user(
    id = UserId("1"),
    email = "foo@example.com",
    firstName = Some("Foo"),
    lastName = Some("John"),
    lastIp = Some("0.0.0.0"),
    hashedPassword = Some("ZAEAZE232323SFSSDF"),
    lastConnection = zonedDateTimeInThePast,
    verificationToken = Some("VERIFTOKEN"),
    verificationTokenExpiresAt = Some(zonedDateTimeInThePast),
    roles = Seq(Role.RoleAdmin, Role.RoleCitizen),
    profile = Some(fooProfile),
    createdAt = Some(zonedDateTimeInThePast)
  )

  val fooCrmUser = PersistentCrmUser(
    userId = "user-id",
    email = "foo@example.com",
    fullName = "Foo",
    firstname = "Foo",
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
    lastCountryActivity = Some("FR"),
    lastLanguageActivity = Some("fr"),
    totalNumberProposals = Some(42),
    totalNumberVotes = Some(1337),
    firstContributionDate = None,
    lastContributionDate = None,
    operationActivity = None,
    sourceActivity = None,
    activeCore = None,
    daysOfActivity = None,
    daysOfActivity30d = None,
    userType = None,
    accountType = None
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
        maybeProperties.dateOfBirth shouldBe Some("2000-01-01 00:00:00")
        maybeProperties.emailValidationStatus shouldBe Some(true)
        maybeProperties.emailHardBounceValue shouldBe Some(false)
        maybeProperties.unsubscribeStatus shouldBe Some(false)
        maybeProperties.accountCreationDate shouldBe Some("2017-06-01 12:30:40")
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
        maybeProperties.accountType shouldBe Some("USER")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(
          maybeProperties.updatedAt.getOrElse(""),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        )
        updatedAt.isBefore(DateHelper.now()) && updatedAt.isAfter(DateHelper.now().minusSeconds(10)) shouldBe true
      }
    }

    scenario("users properties are not normalized after date fix") {
      Given("a registered user without register event and with a recent creation date")
      When("I get user properties")

      val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
      val futureProperties = crmService.getPropertiesFromUser(
        userWithoutRegisteredEventAfterDateFix,
        new QuestionResolver(Seq.empty, Map.empty)
      )
      Then("data are not normalized")
      whenReady(futureProperties, Timeout(3.seconds)) { maybeProperties =>
        maybeProperties.userId shouldBe Some(UserId("user-without-registered-event"))
        maybeProperties.firstName shouldBe Some("Foo")
        maybeProperties.postalCode shouldBe Some("93")
        maybeProperties.dateOfBirth shouldBe Some("2000-01-01 00:00:00")
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
        maybeProperties.accountType shouldBe Some("USER")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(
          maybeProperties.updatedAt.getOrElse(""),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        )
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
        maybeProperties.dateOfBirth shouldBe Some("2000-01-01 00:00:00")
        maybeProperties.emailValidationStatus shouldBe Some(true)
        maybeProperties.emailHardBounceValue shouldBe Some(false)
        maybeProperties.unsubscribeStatus shouldBe Some(false)
        maybeProperties.accountCreationDate shouldBe Some("2017-06-01 12:30:40")
        maybeProperties.accountCreationSource shouldBe Some("core")
        maybeProperties.accountCreationOrigin shouldBe None
        maybeProperties.accountCreationSlug shouldBe Some("chance-aux-jeunes")
        maybeProperties.accountCreationCountry shouldBe Some("FR")
        maybeProperties.countriesActivity shouldBe Some("FR,IT,GB")
        maybeProperties.lastCountryActivity shouldBe Some("FR")
        maybeProperties.lastLanguageActivity shouldBe Some("fr")
        maybeProperties.totalProposals shouldBe Some(1)
        maybeProperties.totalVotes shouldBe Some(5)
        maybeProperties.firstContributionDate shouldBe Some("2017-06-01 12:30:40")
        maybeProperties.lastContributionDate shouldBe Some(
          zonedDateTimeInThePastAt31daysBefore
            .plusDays(2)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC))
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
        maybeProperties.accountType shouldBe Some("USER")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(
          maybeProperties.updatedAt.getOrElse(""),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        )
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
          votesSequenceCount = 42,
          votesSegmentCount = 42,
          toEnrich = true,
          scores = IndexedScores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
          segmentScores = IndexedScores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
          context = None,
          trending = None,
          labels = Seq.empty,
          author = IndexedAuthor(
            firstName = None,
            organisationName = None,
            organisationSlug = None,
            postalCode = None,
            age = None,
            avatarUrl = None,
            anonymousParticipation = false,
            userType = UserType.UserTypeUser
          ),
          organisations = Seq.empty,
          country = Country("FR"),
          language = Language("fr"),
          themeId = None,
          question = Some(IndexedProposalQuestion(questionId1, "question-1", "", "", None, None, isOpen = true)),
          tags = Seq.empty,
          selectedStakeTag = None,
          ideaId = None,
          operationId = Some(OperationId("")),
          sequencePool = SequencePool.New,
          sequenceSegmentPool = SequencePool.New,
          initialProposal = false,
          refusalReason = None,
          operationKind = None,
          segment = None
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
          votesSequenceCount = 42,
          votesSegmentCount = 42,
          toEnrich = true,
          scores = IndexedScores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
          segmentScores = IndexedScores(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
          context = None,
          trending = None,
          labels = Seq.empty,
          author = IndexedAuthor(
            firstName = None,
            organisationName = None,
            organisationSlug = None,
            postalCode = None,
            age = None,
            avatarUrl = None,
            anonymousParticipation = false,
            userType = UserType.UserTypeUser
          ),
          organisations = Seq.empty,
          country = Country("FR"),
          language = Language("fr"),
          themeId = None,
          question = Some(IndexedProposalQuestion(questionId2, "question-2", "", "", None, None, isOpen = true)),
          tags = Seq.empty,
          selectedStakeTag = None,
          ideaId = None,
          operationId = Some(OperationId("")),
          sequencePool = SequencePool.New,
          sequenceSegmentPool = SequencePool.New,
          initialProposal = false,
          refusalReason = None,
          operationKind = None,
          segment = None
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

  feature("synchronizing list") {

    scenario("no user to synchronize") {
      when(
        persistentCrmUserService.list(unsubscribed = Some(false), hardBounced = false, page = 0, numberPerPage = 1000)
      ).thenReturn(Future.successful(Seq.empty))

      whenReady(
        crmService.synchronizeList(
          DateHelper.now().toString,
          CrmList.OptIn,
          CrmList.OptIn.targetDirectory(mailJetConfiguration.csvDirectory)
        ),
        Timeout(2.seconds)
      ) { _ =>
        // Synchro should somewhat end
      }
    }

    scenario("multiple users") {
      when(persistentCrmUserService.list(matches(Some(true)), matches(false), any[Int], any[Int]))
        .thenReturn(
          Future.successful(Seq(persistentCrmUser("1"), persistentCrmUser("2"))),
          Future.successful(Seq(persistentCrmUser("3"), persistentCrmUser("4"))),
          Future.successful(Seq(persistentCrmUser("5"), persistentCrmUser("6"))),
          Future.successful(Seq.empty)
        )

      when(crmClient.sendCsv(any[String], any[Path])(any[ExecutionContext])).thenReturn(
        Future.successful(SendCsvResponse(1L)),
        Future.successful(SendCsvResponse(2L)),
        Future.successful(SendCsvResponse(3L)),
        Future.successful(SendCsvResponse(4L)),
        Future.successful(SendCsvResponse(5L)),
        Future.successful(SendCsvResponse(6L))
      )

      def csvImportResponse(jobId: Long,
                            dataId: Long,
                            errorCount: Int,
                            status: String): BasicCrmResponse[CsvImportResponse] = {
        BasicCrmResponse[CsvImportResponse](
          count = 1,
          total = 1,
          data = Seq(CsvImportResponse(jobId = jobId, dataId = dataId, errorCount = errorCount, status = status))
        )
      }

      when(crmClient.manageContactListWithCsv(any[CsvImport])(any[ExecutionContext])).thenAnswer { invocation =>
        val request = invocation.getArgument[CsvImport](0)
        Future.successful(csvImportResponse(request.csvId.toLong * 10, request.csvId.toLong, 0, "Pending"))
      }

      when(crmClient.monitorCsvImport(matches(10L))(any[ExecutionContext]))
        .thenReturn(
          Future.successful(csvImportResponse(10L, 1L, 0, "Pending")),
          Future.successful(csvImportResponse(10L, 1L, 0, "Pending")),
          Future.successful(csvImportResponse(10L, 1L, 0, "Pending")),
          Future.successful(csvImportResponse(10L, 1L, 0, "Pending")),
          Future.successful(csvImportResponse(10L, 1L, 0, "Completed"))
        )

      when(crmClient.monitorCsvImport(matches(20L))(any[ExecutionContext]))
        .thenReturn(
          Future.successful(csvImportResponse(20L, 2L, 0, "Pending")),
          Future.successful(csvImportResponse(20L, 2L, 0, "Pending")),
          Future.successful(csvImportResponse(20L, 2L, 0, "Pending")),
          Future.successful(csvImportResponse(20L, 2L, 0, "Pending")),
          Future.successful(csvImportResponse(20L, 2L, 0, "Completed"))
        )

      when(crmClient.monitorCsvImport(matches(30L))(any[ExecutionContext]))
        .thenReturn(
          Future.successful(csvImportResponse(30L, 3L, 0, "Pending")),
          Future.successful(csvImportResponse(30L, 3L, 0, "Pending")),
          Future.successful(csvImportResponse(30L, 3L, 0, "Pending")),
          Future.successful(csvImportResponse(30L, 3L, 0, "Pending")),
          Future.successful(csvImportResponse(30L, 3L, 0, "Completed"))
        )

      when(crmClient.monitorCsvImport(matches(40L))(any[ExecutionContext]))
        .thenReturn(
          Future.failed(new IllegalStateException("Don't worry, this exception is fake")),
          Future.successful(csvImportResponse(40L, 4L, 0, "Pending")),
          Future.successful(csvImportResponse(40L, 4L, 0, "Pending")),
          Future.successful(csvImportResponse(40L, 4L, 0, "Pending")),
          Future.successful(csvImportResponse(40L, 4L, 0, "Completed"))
        )

      when(crmClient.monitorCsvImport(matches(50L))(any[ExecutionContext]))
        .thenReturn(
          Future.successful(csvImportResponse(50L, 5L, 0, "Pending")),
          Future.successful(csvImportResponse(50L, 5L, 0, "Pending")),
          Future.successful(csvImportResponse(50L, 5L, 0, "Pending")),
          Future.successful(csvImportResponse(50L, 5L, 0, "Pending")),
          Future.successful(csvImportResponse(50L, 5L, 2, "Completed"))
        )

      when(crmClient.monitorCsvImport(matches(60L))(any[ExecutionContext]))
        .thenReturn(
          Future.successful(csvImportResponse(60L, 6L, 0, "Pending")),
          Future.successful(csvImportResponse(60L, 6L, 0, "Pending")),
          Future.successful(csvImportResponse(60L, 6L, 0, "Pending")),
          Future.successful(csvImportResponse(60L, 6L, 0, "Pending")),
          Future.successful(csvImportResponse(60L, 6L, 2, "Completed"))
        )

      whenReady(
        crmService.synchronizeList(
          DateHelper.now().toString,
          CrmList.OptOut,
          CrmList.OptOut.targetDirectory(mailJetConfiguration.csvDirectory)
        ),
        Timeout(60.seconds)
      ) { _ =>
        Mockito
          .verify(crmClient, Mockito.times(30))
          .monitorCsvImport(any[Long])(any[ExecutionContext])
      }
    }
  }

  feature("create csv") {
    scenario("create csv from contact") {
      val contact = Contact(
        email = "test@exemple.com",
        name = Some("test"),
        properties = Some(
          ContactProperties(
            userId = Some(UserId("user")),
            firstName = Some("test"),
            postalCode = None,
            dateOfBirth = Some("1992-01-01 00:00:00"),
            emailValidationStatus = Some(true),
            emailHardBounceValue = Some(false),
            unsubscribeStatus = Some(false),
            accountCreationDate = Some("2019-10-07 10:45:10"),
            accountCreationSource = None,
            accountCreationOrigin = None,
            accountCreationSlug = None,
            accountCreationCountry = Some("FR"),
            countriesActivity = Some("FR"),
            lastCountryActivity = Some("FR"),
            lastLanguageActivity = Some("fr"),
            totalProposals = Some(42),
            totalVotes = Some(1337),
            firstContributionDate = Some("2019-04-15 15:24:17"),
            lastContributionDate = Some("2019-10-07 10:47:42"),
            operationActivity = None,
            sourceActivity = None,
            activeCore = None,
            daysOfActivity = None,
            daysOfActivity30 = None,
            userType = None,
            accountType = None,
            updatedAt = Some("2019-10-06 02:00:00")
          )
        )
      )

      contact.toStringCsv should be(
        s""""test@exemple.com","user","test",,"1992-01-01 00:00:00","true","false","false","2019-10-07 10:45:10",,,,"FR","FR","FR","fr","42","1337","2019-04-15 15:24:17","2019-10-07 10:47:42",,,,,,,,"2019-10-06 02:00:00"${String
          .format("%n")}"""
      )
    }
  }

  feature("create csv stream") {
    scenario("create one csv") {
      when(
        persistentCrmUserService
          .list(
            unsubscribed = CrmList.OptIn.unsubscribed,
            hardBounced = CrmList.OptIn.hardBounced,
            0,
            mailJetConfiguration.userListBatchSize
          )
      ).thenReturn(
        Future.successful(Seq(fooCrmUser, fooCrmUser.copy(userId = "user-id-2", email = "foo2@example.com")))
      )

      when(
        persistentCrmUserService
          .list(
            unsubscribed = CrmList.OptIn.unsubscribed,
            hardBounced = CrmList.OptIn.hardBounced,
            2,
            mailJetConfiguration.userListBatchSize
          )
      ).thenReturn(Future.successful(Seq.empty))

      val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

      val formattedDate = DateHelper.now().format(dateFormatter)

      whenReady(
        crmService.createCsv(formattedDate, CrmList.OptIn, CrmList.OptIn.targetDirectory("/tmp/make/crm")),
        Timeout(30.seconds)
      ) { fileList =>
        val file = fileList.head
        val bufferedFile = io.Source.fromFile(file.toFile)
        val fileToSeq = bufferedFile.getLines.toSeq
        val lineCount = fileToSeq.size
        val firstLine = fileToSeq.head
        lineCount should be(2)
        firstLine should be(
          s""""foo@example.com","user-id","Foo",,,"true","false","false",,,,,,,"FR","fr","42","1337",,,,,,,,,,"$formattedDate""""
        )
        bufferedFile.close()
      }
    }

    scenario("split into 2 csv") {

      def crmUser(id: String) = {
        fooCrmUser.copy(userId = s"user-id-$id", email = s"foo$id@example.com")
      }

      when(
        persistentCrmUserService
          .list(
            unsubscribed = CrmList.OptIn.unsubscribed,
            hardBounced = CrmList.OptIn.hardBounced,
            0,
            mailJetConfiguration.userListBatchSize
          )
      ).thenReturn(Future.successful(Seq(crmUser("1"), crmUser("2"), crmUser("3"), crmUser("4"), crmUser("5"))))

      when(
        persistentCrmUserService
          .list(
            unsubscribed = CrmList.OptIn.unsubscribed,
            hardBounced = CrmList.OptIn.hardBounced,
            5,
            mailJetConfiguration.userListBatchSize
          )
      ).thenReturn(Future.successful(Seq.empty))

      val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)

      val formattedDate = DateHelper.now().format(dateFormatter)

      whenReady(
        crmService.createCsv(formattedDate, CrmList.OptIn, CrmList.OptIn.targetDirectory("/tmp/make/crm")),
        Timeout(30.seconds)
      ) { fileList =>
        fileList.size > 1 should be(true)
      }
    }
  }

  feature("Resend emails on error") {

    scenario("sending email randomly fails") {
      forAll { (message: SendEmail, succeed: Boolean) =>
        Given("an unreliable email provider")
        when(crmClient.sendEmail(matches(SendMessages(message)))(any[ExecutionContext]))
          .thenReturn(if (succeed) Future.successful(SendEmailResponse(Nil)) else Future.failed(new Exception("Don't worry, this exception is fake")))
        When("sending an email")
        crmService.sendEmail(message)
        Then("the message should be rescheduled if sending failed")
        verify(eventBusService, new After(1000, times(if (succeed) 0 else 1))).publish(matches(message))
      }
    }

  }

}

object CrmServiceComponentTest {
  val configuration: String = "akka.log-dead-letters-during-shutdown = off"
  val actorSystem = ActorSystem("CrmServiceComponentTest", ConfigFactory.parseString(configuration))
}

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

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, SpawnProtocol}
import akka.http.scaladsl.model.StatusCodes
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.scaladsl.Source
import cats.data.NonEmptyList
import com.typesafe.config.ConfigFactory
import org.make.api._
import org.make.api.extensions.{MailJetConfiguration, MailJetConfigurationComponent}
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.proposal.{ProposalService, ProposalServiceComponent}
import org.make.api.question.{QuestionService, QuestionServiceComponent, SearchQuestionRequest}
import org.make.api.technical._
import org.make.api.technical.crm.CrmClient.{Account, Marketing, Transactional}
import org.make.api.technical.crm.CrmServiceComponentTest.configuration
import org.make.api.technical.crm.ManageContactAction.Remove
import org.make.api.technical.crm.arbitraries._
import org.make.api.technical.job.{JobCoordinatorService, JobCoordinatorServiceComponent}
import org.make.api.user.PersistentCrmSynchroUserService.{CrmSynchroUser, MakeUser}
import org.make.api.user.{
  PersistentCrmSynchroUserService,
  PersistentCrmSynchroUserServiceComponent,
  PersistentUserToAnonymizeService,
  PersistentUserToAnonymizeServiceComponent
}
import org.make.api.userhistory._
import org.make.core.history.HistoryActions.VoteTrust.Trusted
import org.make.core.operation._
import org.make.core.proposal.ProposalActionType.ProposalVoteAction
import org.make.core.proposal.ProposalStatus.Accepted
import org.make.core.proposal._
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.session.SessionId
import org.make.core.user.{UserId, UserType}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.Mockito.clearInvocations
import org.mockito.verification.After
import org.scalatest.PrivateMethodTester
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.io.{BufferedReader, InputStreamReader}
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.{Source => IOSource}

class CrmServiceComponentTest
    extends MakeUnitTest
    with DefaultCrmServiceComponent
    with OperationServiceComponent
    with QuestionServiceComponent
    with MailJetConfigurationComponent
    with ActorSystemTypedComponent
    with UserHistoryCoordinatorServiceComponent
    with PersistentCrmSynchroUserServiceComponent
    with ReadJournalComponent
    with ProposalServiceComponent
    with PersistentUserToAnonymizeServiceComponent
    with CrmClientComponent
    with PersistentCrmUserServiceComponent
    with EventBusServiceComponent
    with JobCoordinatorServiceComponent
    with ScalaCheckDrivenPropertyChecks
    with PrivateMethodTester
    with DefaultSpawnActorServiceComponent
    with SpawnActorRefComponent {

  override implicit val actorSystemTyped: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty[Nothing], "CrmServiceComponentTest", ConfigFactory.parseString(configuration))
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val proposalJournal: CassandraReadJournal = mock[CassandraReadJournal]
  override val userJournal: CassandraReadJournal = mock[CassandraReadJournal]
  override val sessionJournal: CassandraReadJournal = mock[CassandraReadJournal]
  override val mailJetConfiguration: MailJetConfiguration = mock[MailJetConfiguration]
  override val operationService: OperationService = mock[OperationService]
  override val questionService: QuestionService = mock[QuestionService]
  override val persistentUserToAnonymizeService: PersistentUserToAnonymizeService =
    mock[PersistentUserToAnonymizeService]

  override val crmClient: CrmClient = mock[CrmClient]
  override val persistentCrmUserService: PersistentCrmUserService = mock[PersistentCrmUserService]
  override val eventBusService: EventBusService = mock[EventBusService]
  override val proposalService: ProposalService = mock[ProposalService]
  override val jobCoordinatorService: JobCoordinatorService = mock[JobCoordinatorService]
  override val spawnActorRef: ActorRef[SpawnProtocol.Command] =
    actorSystemTyped.systemActorOf(SpawnProtocol(), "spawner")
  override val persistentCrmSynchroUserService: PersistentCrmSynchroUserService = mock[PersistentCrmSynchroUserService]

  when(mailJetConfiguration.userListBatchSize).thenReturn(1000)
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
      accountCreationLocation = None,
      countriesActivity = None,
      lastCountryActivity = None,
      totalNumberProposals = None,
      totalNumberVotes = None,
      firstContributionDate = None,
      lastContributionDate = None,
      operationActivity = None,
      sourceActivity = None,
      daysOfActivity = None,
      daysOfActivity30d = None,
      userType = None,
      accountType = None,
      daysBeforeDeletion = None,
      lastActivityDate = None,
      sessionsCount = None,
      eventsCount = None
    )
  }

  val user: CrmSynchroUser = MakeUser(
    uuid = UserId("50b3d4f6-4bfe-4102-94b1-bfcfdf12ef74"),
    email = "alex.terrieur@gmail.com",
    firstName = Some("Alex"),
    lastName = Some("Terrieur"),
    organisationName = None,
    postalCode = None,
    dateOfBirth = None,
    emailVerified = false,
    isHardBounce = false,
    optInNewsletter = false,
    createdAt = DateHelper.now(),
    userType = UserType.UserTypeUser,
    country = Country("FR"),
    lastConnection = Some(DateHelper.now()),
    registerQuestionId = None
  )

  val handledUserIds: Seq[UserId] = Seq(UserId("8c0dcb2a-d4f8-4514-b1f1-8077ba314594"))

  when(
    proposalService
      .resolveQuestionFromUserProposal(
        any[QuestionResolver],
        any[RequestContext],
        eqTo(UserId("50b3d4f6-4bfe-4102-94b1-bfcfdf12ef74")),
        any[ZonedDateTime]
      )
  ).thenAnswer { (resolver: QuestionResolver, requestContext: RequestContext, _: UserId, _: ZonedDateTime) =>
    val maybeQuestion = resolver.extractQuestionWithOperationFromRequestContext(requestContext)
    Future.successful(maybeQuestion)
  }

  when(
    proposalService
      .resolveQuestionFromUserProposal(
        any[QuestionResolver],
        any[RequestContext],
        eqTo(UserId("1")),
        any[ZonedDateTime]
      )
  ).thenAnswer { (resolver: QuestionResolver, requestContext: RequestContext, _: UserId, _: ZonedDateTime) =>
    Future.successful(resolver.extractQuestionWithOperationFromRequestContext(requestContext))
  }

  def readEvents(resource: String, userId: UserId = user.uuid): Source[EventEnvelope, NotUsed] = {
    val file = getClass.getClassLoader.getResourceAsStream(resource)
    val reader = new BufferedReader(new InputStreamReader(file))
    val events = reader
      .lines()
      .map[Object] { line =>
        val splitted = line.split(":", 3)
        StaminaTestUtils.deserializeEventFromJson[Object](actorSystemTyped, splitted(0), splitted(2), splitted(1).toInt)
      }
      .toArray
      .toIndexedSeq

    Source(events.map(EventEnvelope(Offset.noOffset, userId.value, 0L, _, DateHelper.now().toEpochSecond)))
  }

  when(mailJetConfiguration.url).thenReturn("http://localhost:1234")
  when(mailJetConfiguration.userListBatchSize).thenReturn(1000)
  when(mailJetConfiguration.httpBufferSize).thenReturn(200)
  when(mailJetConfiguration.campaignApiKey).thenReturn("api-key")
  when(mailJetConfiguration.campaignSecretKey).thenReturn("secret-key")
  when(mailJetConfiguration.csvSize).thenReturn(300)
  when(mailJetConfiguration.csvDirectory).thenReturn("/tmp/make/crm")

  when(persistentUserToAnonymizeService.removeAllByEmails(any[Seq[String]])).thenAnswer { s: Seq[String] =>
    Future.successful(s.size)
  }

  val questionFr: Question = Question(
    questionId = QuestionId("question-fr"),
    slug = "question-fr",
    countries = NonEmptyList.of(Country("FR")),
    language = Language("fr"),
    question = "question ?",
    shortTitle = None,
    operationId = Some(OperationId("999-99-99"))
  )

  val questionGb: Question = Question(
    questionId = QuestionId("question-gb"),
    slug = "question-gb",
    countries = NonEmptyList.of(Country("GB")),
    language = Language("en"),
    question = "question ?",
    shortTitle = None,
    operationId = Some(OperationId("888-88-88"))
  )

  val questionIt: Question = Question(
    questionId = QuestionId("question-it"),
    slug = "question-it",
    countries = NonEmptyList.of(Country("IT")),
    language = Language("it"),
    question = "question ?",
    shortTitle = None,
    operationId = Some(OperationId("777-77-77"))
  )

  val proposalFr: Proposal = proposal(id = ProposalId("proposalId-fr"), questionId = questionFr.questionId)
  val proposalGb: Proposal =
    proposalFr.copy(proposalId = ProposalId("proposalId-gb"), questionId = Some(questionGb.questionId))
  val proposalIt: Proposal =
    proposalFr.copy(proposalId = ProposalId("proposalId-it"), questionId = Some(questionIt.questionId))

  val proposals =
    Map(proposalFr.proposalId -> proposalFr, proposalIt.proposalId -> proposalIt, proposalGb.proposalId -> proposalGb)

  when(
    proposalService
      .resolveQuestionFromVoteEvent(any[QuestionResolver], any[RequestContext], any[ProposalId])
  ).thenAnswer { (questionResolver: QuestionResolver, requestContext: RequestContext, proposalId: ProposalId) =>
    val maybeProposal = proposals.get(proposalId)
    val maybeQuestion =
      questionResolver
        .extractQuestionWithOperationFromRequestContext(requestContext)
        .orElse(
          maybeProposal.flatMap(
            proposal =>
              questionResolver.findQuestionWithOperation { question =>
                proposal.questionId.contains(question.questionId)
              }
          )
        )
    Future.successful(maybeQuestion)
  }

  val defaultOperation: Operation = Operation(
    status = OperationStatus.Active,
    operationId = OperationId("default"),
    slug = "default",
    operationKind = OperationKind.BusinessConsultation,
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

  val fooUser: CrmSynchroUser = MakeUser(
    uuid = UserId("1"),
    email = "foo@example.com",
    firstName = Some("Foo"),
    lastName = Some("John"),
    lastConnection = Some(zonedDateTimeInThePast),
    createdAt = zonedDateTimeInThePast,
    organisationName = None,
    postalCode = Some("93"),
    dateOfBirth = Some(LocalDate.parse("2000-01-01")),
    emailVerified = true,
    isHardBounce = false,
    optInNewsletter = true,
    userType = UserType.UserTypeUser,
    country = Country("FR"),
    registerQuestionId = None
  )

  val fooCrmUser: PersistentCrmUser = PersistentCrmUser(
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
    accountCreationLocation = None,
    countriesActivity = None,
    lastCountryActivity = Some("FR"),
    totalNumberProposals = Some(42),
    totalNumberVotes = Some(1337),
    firstContributionDate = None,
    lastContributionDate = None,
    operationActivity = None,
    sourceActivity = None,
    daysOfActivity = None,
    daysOfActivity30d = None,
    userType = None,
    accountType = None,
    daysBeforeDeletion = None,
    lastActivityDate = None,
    sessionsCount = None,
    eventsCount = None
  )

  val registerCitizenEventEnvelope: EventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "foo-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogRegisterCitizenEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("core"),
        operationId = Some(OperationId("999-99-99")),
        sessionId = SessionId("id1"),
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
    ),
    timestamp = DateHelper.now().toEpochSecond
  )

  val userProposalEventEnvelope: EventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserProposalEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("core"),
        operationId = Some(OperationId("vff")),
        sessionId = SessionId("id1"),
        country = Some(Country("IT")),
        language = Some(Language("it"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePast,
        actionType = LogUserProposalEvent.action,
        arguments = UserProposal(content = "il faut proposer", theme = Some(ThemeId("my-theme")))
      )
    ),
    timestamp = DateHelper.now().toEpochSecond
  )

  val userVoteEventEnvelope: EventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserVoteEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("vff"),
        sessionId = SessionId("id1"),
        country = Some(Country("GB")),
        language = Some(Language("uk"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePast,
        actionType = ProposalVoteAction.value,
        arguments = UserVote(proposalId = ProposalId("proposalId-gb"), voteKey = VoteKey.Neutral, trust = Trusted)
      )
    ),
    timestamp = DateHelper.now().toEpochSecond
  )

  val userVoteEventEnvelope2: EventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserVoteEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("culture"),
        operationId = Some(OperationId("culture")),
        sessionId = SessionId("id2"),
        country = Some(Country("FR")),
        language = Some(Language("fr"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePast,
        actionType = ProposalVoteAction.value,
        arguments = UserVote(proposalId = ProposalId("proposalId-fr"), voteKey = VoteKey.Agree, trust = Trusted)
      )
    ),
    timestamp = DateHelper.now().toEpochSecond
  )

  val userVoteEventEnvelope3: EventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserVoteEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("culture"),
        operationId = Some(OperationId("culture")),
        sessionId = SessionId("id3"),
        country = Some(Country("FR")),
        language = Some(Language("fr"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePastAt31daysBefore,
        actionType = ProposalVoteAction.value,
        arguments = UserVote(proposalId = ProposalId("proposalId-fr"), voteKey = VoteKey.Agree, trust = Trusted)
      )
    ),
    timestamp = DateHelper.now().toEpochSecond
  )

  val userVoteEventEnvelope4: EventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserVoteEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("culture"),
        operationId = Some(OperationId("invalidoperation")),
        sessionId = SessionId("id3"),
        country = Some(Country("FR")),
        language = Some(Language("fr"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePastAt31daysBefore.plusDays(2),
        actionType = ProposalVoteAction.value,
        arguments = UserVote(proposalId = ProposalId("proposalId-fr"), voteKey = VoteKey.Agree, trust = Trusted)
      )
    ),
    timestamp = DateHelper.now().toEpochSecond
  )

  val userVoteEventEnvelope5: EventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserVoteEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("culture"),
        operationId = Some(OperationId("200-20-11")),
        sessionId = SessionId("id4"),
        country = Some(Country("FR")),
        language = Some(Language("fr"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePastAt31daysBefore.plusDays(2),
        actionType = ProposalVoteAction.value,
        arguments = UserVote(proposalId = ProposalId("proposalId"), voteKey = VoteKey.Agree, trust = Trusted)
      )
    ),
    timestamp = DateHelper.now().toEpochSecond
  )

  val userConnectedEventEnvelope: EventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "bar-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogUserConnectedEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("culture"),
        operationId = Some(OperationId("200-20-11")),
        sessionId = SessionId("id5"),
        country = Some(Country("FR")),
        language = Some(Language("fr"))
      ),
      action = UserAction(
        date = zonedDateTimeInThePastAt31daysBefore.plusDays(15),
        actionType = UserHasConnected.actionType,
        arguments = UserHasConnected()
      )
    ),
    timestamp = DateHelper.now().toEpochSecond
  )

  when(userJournal.currentEventsByPersistenceId(eqTo(fooUser.uuid.value), any[Long], any[Long]))
    .thenReturn(
      Source(
        List(
          registerCitizenEventEnvelope,
          userProposalEventEnvelope,
          userVoteEventEnvelope,
          userVoteEventEnvelope2,
          userVoteEventEnvelope3,
          userVoteEventEnvelope4,
          userVoteEventEnvelope5,
          userConnectedEventEnvelope
        )
      )
    )

  val userWithoutRegisteredEvent: CrmSynchroUser = fooUser.copy(uuid = UserId("user-without-registered-event"))
  val userWithoutRegisteredEventAfterDateFix: CrmSynchroUser =
    fooUser.copy(uuid = UserId("user-without-registered-event"), createdAt = zonedDateTimeNow)

  when(userJournal.currentEventsByPersistenceId(eqTo(userWithoutRegisteredEvent.uuid.value), any[Long], any[Long]))
    .thenReturn(Source(List.empty))

  Feature("data normalization") {
    Scenario("users properties are normalized when user has no register event") {
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
        maybeProperties.totalProposals shouldBe Some(0)
        maybeProperties.totalVotes shouldBe Some(0)
        maybeProperties.firstContributionDate shouldBe None
        maybeProperties.lastContributionDate shouldBe None
        maybeProperties.operationActivity shouldBe Some("")
        maybeProperties.daysOfActivity shouldBe Some(0)
        maybeProperties.daysOfActivity30 shouldBe Some(0)
        maybeProperties.userType shouldBe Some("B2C")
        maybeProperties.accountType shouldBe Some("USER")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(
          maybeProperties.updatedAt.getOrElse(""),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        )
        updatedAt.isBefore(DateHelper.now()) && updatedAt.isAfter(DateHelper.now().minusSeconds(10)) shouldBe true
        val lastActivityDate = ZonedDateTime.parse(
          maybeProperties.lastActivityDate.getOrElse(""),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        )
        lastActivityDate.isEqual(zonedDateTimeInThePast) shouldBe true
        val deletionDate = lastActivityDate.plusYears(2).plusMonths(11)
        maybeProperties.daysBeforeDeletion shouldBe Some(
          ChronoUnit.DAYS.between(ZonedDateTime.now(), deletionDate).toInt
        )
        maybeProperties.sessionsCount shouldBe Some(0)
        maybeProperties.eventsCount shouldBe Some(0)
      }
    }

    Scenario("users properties are not normalized after date fix") {
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
        maybeProperties.totalProposals shouldBe Some(0)
        maybeProperties.totalVotes shouldBe Some(0)
        maybeProperties.firstContributionDate shouldBe None
        maybeProperties.lastContributionDate shouldBe None
        maybeProperties.operationActivity shouldBe Some("")
        maybeProperties.daysOfActivity shouldBe Some(0)
        maybeProperties.daysOfActivity30 shouldBe Some(0)
        maybeProperties.userType shouldBe Some("B2C")
        maybeProperties.accountType shouldBe Some("USER")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(
          maybeProperties.updatedAt.getOrElse(""),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        )
        updatedAt.isBefore(DateHelper.now()) && updatedAt.isAfter(DateHelper.now().minusSeconds(10)) shouldBe true
        val lastActivityDate = ZonedDateTime.parse(
          maybeProperties.lastActivityDate.getOrElse(""),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        )
        lastActivityDate.isEqual(zonedDateTimeInThePast) shouldBe true
        val deletionDate = lastActivityDate.plusYears(2).plusMonths(11)
        maybeProperties.daysBeforeDeletion shouldBe Some(
          ChronoUnit.DAYS.between(ZonedDateTime.now(), deletionDate).toInt
        )
        maybeProperties.sessionsCount shouldBe Some(0)
        maybeProperties.eventsCount shouldBe Some(0)
      }
    }

    Scenario("Date before deletion is changed based on events") {
      when(userJournal.currentEventsByPersistenceId(eqTo(fooUser.uuid.value), any[Long], any[Long]))
        .thenReturn(
          Source(
            List(
              registerCitizenEventEnvelope,
              userProposalEventEnvelope,
              userVoteEventEnvelope,
              userVoteEventEnvelope2,
              userVoteEventEnvelope3
            )
          ),
          Source(
            List(
              registerCitizenEventEnvelope,
              userProposalEventEnvelope,
              userVoteEventEnvelope,
              userVoteEventEnvelope2,
              userVoteEventEnvelope3,
              userVoteEventEnvelope4,
              userVoteEventEnvelope5,
              userConnectedEventEnvelope
            )
          )
        )

      def futureProperties = crmService.getPropertiesFromUser(fooUser, new QuestionResolver(Seq.empty, Map.empty))
      var dateBeforeDeletion1: Option[Int] = None
      var dateBeforeDeletion2: Option[Int] = None
      whenReady(futureProperties, Timeout(3.seconds)) { properties =>
        dateBeforeDeletion1 = properties.daysBeforeDeletion
      }
      whenReady(futureProperties, Timeout(3.seconds)) { properties =>
        dateBeforeDeletion2 = properties.daysBeforeDeletion
      }
      (dateBeforeDeletion1 should not).equal(dateBeforeDeletion2)
      dateBeforeDeletion1 should be < dateBeforeDeletion2
    }

    Scenario("B2B users are anonymized after 5 years") {
      val organisation = fooUser.copy(userType = UserType.UserTypeOrganisation)
      whenReady(
        crmService.getPropertiesFromUser(organisation, new QuestionResolver(Seq.empty, Map.empty)),
        Timeout(3.seconds)
      ) { properties =>
        properties.daysBeforeDeletion shouldBe properties.lastActivityDate.map(
          date =>
            ChronoUnit.DAYS
              .between(
                ZonedDateTime.now(),
                ZonedDateTime
                  .parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC))
                  .plusYears(4)
                  .plusMonths(11)
              )
              .toInt
        )
      }
    }
  }

  Feature("add user to OptInList") {
    Scenario("get properties from user and his events") {
      Given("a registred user")

      When("I add a user into optInList")

      when(questionService.searchQuestion(any[SearchQuestionRequest])).thenReturn(
        Future.successful(
          Seq(
            Question(
              questionId = QuestionId("question-id"),
              slug = "question",
              countries = NonEmptyList.of(Country("FR")),
              language = Language("fr"),
              question = "question ?",
              shortTitle = None,
              operationId = Some(OperationId("999-99-99"))
            )
          )
        )
      )

      Then("The properties are calculated from UserHistory")

      val questions = Seq(
        Question(
          questionId = QuestionId("vff-fr"),
          slug = "vff-fr",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "200-20-11",
          shortTitle = None,
          operationId = Some(OperationId("200-20-11"))
        ),
        Question(
          questionId = QuestionId("culture"),
          slug = "culture",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "345-34-89",
          shortTitle = None,
          operationId = Some(OperationId("345-34-89"))
        ),
        Question(
          questionId = QuestionId("chance-aux-jeunes"),
          slug = "chance-aux-jeunes",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "999-99-99",
          shortTitle = None,
          operationId = Some(OperationId("999-99-99"))
        )
      )

      val futureProperties = crmService.getPropertiesFromUser(
        fooUser,
        new QuestionResolver(questions, operations.map(operation => operation.slug -> operation.operationId).toMap)
      )

      whenReady(futureProperties, Timeout(300000.seconds)) { maybeProperties =>
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
        maybeProperties.daysOfActivity shouldBe Some(3)
        maybeProperties.daysOfActivity30 shouldBe Some(1)
        maybeProperties.userType shouldBe Some("B2C")
        maybeProperties.accountType shouldBe Some("USER")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(
          maybeProperties.updatedAt.getOrElse(""),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        )
        updatedAt.isBefore(DateHelper.now()) && updatedAt.isAfter(DateHelper.now().minusSeconds(10)) shouldBe true
        val lastActivityDate = ZonedDateTime.parse(
          maybeProperties.lastActivityDate.getOrElse(""),
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
        )
        lastActivityDate.isBefore(zonedDateTimeInThePastAt31daysBefore.plusDays(15).plusSeconds(1)) && lastActivityDate
          .isAfter(zonedDateTimeInThePastAt31daysBefore.plusDays(15).minusSeconds(1)) shouldBe true
        val deletionDate = lastActivityDate.plusYears(2).plusMonths(11)
        maybeProperties.daysBeforeDeletion shouldBe Some(
          ChronoUnit.DAYS.between(ZonedDateTime.now(), deletionDate).toInt
        )
        maybeProperties.sessionsCount shouldBe Some(5)
        maybeProperties.eventsCount shouldBe Some(8)
      }
    }
  }

  Feature("anonymize") {
    val emails: Seq[String] = Seq("toto@tata.com", "tata@tata.com", "titi@tata.com")

    Scenario("all users anonymized") {
      when(crmClient.deleteContactByEmail(eqTo("toto@tata.com"), eqTo(Marketing))(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(crmClient.deleteContactByEmail(eqTo("tata@tata.com"), eqTo(Marketing))(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(crmClient.deleteContactByEmail(eqTo("titi@tata.com"), eqTo(Marketing))(any[ExecutionContext]))
        .thenReturn(Future.successful(true))

      val futureRemovedEmails: Future[Unit] = crmService.deleteAnonymizedContacts(emails, Marketing)

      whenReady(futureRemovedEmails, Timeout(3.seconds)) { _ =>
        verify(persistentUserToAnonymizeService)
          .removeAllByEmails(Seq("toto@tata.com", "tata@tata.com", "titi@tata.com"))
      }
    }

    Scenario("one users not found in mailjet") {
      when(crmClient.deleteContactByEmail(eqTo("toto@tata.com"), eqTo(Marketing))(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(crmClient.deleteContactByEmail(eqTo("tata@tata.com"), eqTo(Marketing))(any[ExecutionContext]))
        .thenReturn(Future.successful(true))
      when(crmClient.deleteContactByEmail(eqTo("titi@tata.com"), eqTo(Marketing))(any[ExecutionContext]))
        .thenReturn(Future.successful(false))

      val futureRemovedEmails: Future[Unit] = crmService.deleteAnonymizedContacts(emails, Marketing)

      whenReady(futureRemovedEmails, Timeout(3.seconds)) { _ =>
        verify(persistentUserToAnonymizeService)
          .removeAllByEmails(Seq("toto@tata.com", "tata@tata.com"))
      }
    }

    Scenario("zero users anonymized") {
      when(crmClient.deleteContactByEmail(eqTo("toto@tata.com"), eqTo(Marketing))(any[ExecutionContext]))
        .thenReturn(Future.successful(false))
      when(crmClient.deleteContactByEmail(eqTo("tata@tata.com"), eqTo(Marketing))(any[ExecutionContext]))
        .thenReturn(Future.successful(false))
      when(crmClient.deleteContactByEmail(eqTo("titi@tata.com"), eqTo(Marketing))(any[ExecutionContext]))
        .thenReturn(Future.successful(false))

      clearInvocations(persistentUserToAnonymizeService)

      val futureRemovedEmails: Future[Unit] = crmService.deleteAnonymizedContacts(emails, Marketing)

      whenReady(futureRemovedEmails, Timeout(3.seconds)) { _ =>
        verify(persistentUserToAnonymizeService, never).removeAllByEmails(any[Seq[String]])
      }
    }

    Scenario("invalid email") {

      val futureRemovedEmails: Future[Unit] = crmService.deleteAnonymizedContacts(Seq("invalid"), Marketing)

      whenReady(futureRemovedEmails, Timeout(3.seconds)) { _ =>
        verify(persistentUserToAnonymizeService)
          .removeAllByEmails(Seq("invalid"))
      }
    }

    Scenario("anonymize in crm manager") {
      clearInvocations(crmClient)

      val emails = Seq("first@example.com", "second@example.com", "notanemail")

      when(persistentUserToAnonymizeService.findAll())
        .thenReturn(Future.successful(emails))
      when(
        crmClient
          .manageContactList(
            manageContactList = eqTo(
              ManageManyContacts(
                contacts = emails.map(Contact(_)),
                contactList = Seq(
                  ContactList(mailJetConfiguration.hardBounceListId, Remove),
                  ContactList(mailJetConfiguration.unsubscribeListId, Remove),
                  ContactList(mailJetConfiguration.optInListId, Remove)
                )
              )
            ),
            account = any[Account]
          )(any[ExecutionContext])
      ).thenReturn(Future.successful(BasicCrmResponse[JobId](0, 0, Seq.empty)))

      emails.foreach { email =>
        when(crmClient.deleteContactByEmail(eqTo(email), any[Account])(any[ExecutionContext]))
          .thenReturn(Future.successful(true))
      }

      whenReady(crmService.anonymize(), Timeout(10.seconds)) { _ =>
        verify(crmClient).deleteContactByEmail(eqTo("first@example.com"), eqTo(Marketing))(any[ExecutionContext])
        verify(crmClient).deleteContactByEmail(eqTo("first@example.com"), eqTo(Transactional))(any[ExecutionContext])
        verify(crmClient).deleteContactByEmail(eqTo("second@example.com"), eqTo(Marketing))(any[ExecutionContext])
        verify(crmClient).deleteContactByEmail(eqTo("second@example.com"), eqTo(Transactional))(any[ExecutionContext])

        verify(crmClient, never).deleteContactByEmail(eqTo("notanemail"), any[Account])(any[ExecutionContext])
      }
    }
  }

  Feature("read events") {
    Scenario("user 50b3d4f6-4bfe-4102-94b1-bfcfdf12ef74") {
      val source = readEvents("events/user-50b3d4f6-4bfe-4102-94b1-bfcfdf12ef74")

      val operationId = OperationId("a818ef52-cd54-4aa7-bd3d-67e7bf4c4ea5")

      val question = Question(
        questionId = QuestionId("7d2ba29b-d503-44b8-98a0-5b9ae8b8bc69"),
        slug = "my-question",
        countries = NonEmptyList.of(Country("FR")),
        language = Language("fr"),
        question = "Comment sauver le monde ?",
        shortTitle = None,
        operationId = Some(operationId)
      )
      val resolver = new QuestionResolver(Seq(question), Map())

      when(userJournal.currentEventsByPersistenceId(eqTo(user.uuid.value), any[Long], any[Long]))
        .thenReturn(source)

      whenReady(crmService.getPropertiesFromUser(user, resolver), Timeout(5.seconds)) { result =>
        result.operationActivity should contain(question.slug)
        val persistentUser = result.toPersistentCrmUser(user.email, user.fullName.get)
        persistentUser.operationActivity should contain(question.slug)
        persistentUser.totalNumberProposals should contain(2)
      }
    }
    Scenario("user 8c0dcb2a-d4f8-4514-b1f1-8077ba314594") {
      val userId = UserId("8c0dcb2a-d4f8-4514-b1f1-8077ba314594")
      val source = readEvents("events/user-8c0dcb2a-d4f8-4514-b1f1-8077ba314594", userId)

      val questionId1 = QuestionId("question-1")
      val questionId2 = QuestionId("question-2")

      val operationId1 = OperationId("a818ef52-cd54-4aa7-bd3d-67e7bf4c4ea5")

      val questions =
        Seq(
          Question(
            questionId = questionId1,
            slug = "question-1",
            countries = NonEmptyList.of(Country("FR")),
            language = Language("fr"),
            question = "Comment sauver le monde ?",
            shortTitle = None,
            operationId = Some(operationId1)
          ),
          Question(
            questionId = questionId2,
            slug = "question-2",
            countries = NonEmptyList.of(Country("FR")),
            language = Language("fr"),
            question = "Comment resauver le monde ?",
            shortTitle = None,
            operationId = Some(OperationId("who cares?"))
          )
        )

      val resolver = new QuestionResolver(questions, Map())

      val proposal1Date = ZonedDateTime.parse("2019-03-18T23:00:31.243Z")
      val proposal2Date = ZonedDateTime.parse("2019-03-18T23:21:03.501Z")

      val proposals = Seq(
        indexedProposal(
          id = ProposalId("proposal-1"),
          userId = userId,
          content = "proposal 1",
          status = Accepted,
          createdAt = proposal1Date,
          updatedAt = None,
          questionId = questionId1,
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          startDate = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
          endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
          operationId = Some(OperationId(""))
        ),
        indexedProposal(
          id = ProposalId("proposal-2"),
          userId = userId,
          content = "proposal 2",
          status = Accepted,
          createdAt = proposal2Date,
          updatedAt = None,
          questionId = questionId2,
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          startDate = ZonedDateTime.parse("1968-07-03T00:00:00.000Z"),
          endDate = ZonedDateTime.parse("2068-07-03T00:00:00.000Z"),
          operationId = Some(OperationId(""))
        )
      )

      when(
        proposalService
          .resolveQuestionFromUserProposal(any[QuestionResolver], any[RequestContext], eqTo(userId), any[ZonedDateTime])
      ).thenAnswer { (resolver: QuestionResolver, requestContext: RequestContext, _: UserId, date: ZonedDateTime) =>
        val maybeQuestion: Option[Question] =
          resolver.extractQuestionWithOperationFromRequestContext(requestContext).orElse {
            proposals.find(_.createdAt == date).flatMap(_.question).map(_.questionId).flatMap { questionId =>
              resolver.findQuestionWithOperation(_.questionId == questionId)
            }
          }
        Future.successful(maybeQuestion)
      }

      when(userJournal.currentEventsByPersistenceId(eqTo(userId.value), any[Long], any[Long]))
        .thenReturn(source)

      whenReady(crmService.getPropertiesFromUser(user.copy(uuid = userId), resolver), Timeout(5.seconds)) { result =>
        result.operationActivity.toSeq.flatMap(_.split(",").sorted) should be(Seq("question-1", "question-2"))
        result.totalProposals should contain(2)
      }
    }

    Scenario("Fallback to user's creation country when register event doesn't have one") {
      val source = readEvents("events/user-without-registration-country")

      val resolver = new QuestionResolver(Nil, Map())

      val userWithCountry = user.copy(country = Country("BE"))

      when(userJournal.currentEventsByPersistenceId(eqTo(userWithCountry.uuid.value), any[Long], any[Long]))
        .thenReturn(source)

      whenReady(crmService.getPropertiesFromUser(userWithCountry, resolver), Timeout(5.seconds)) { result =>
        result.accountCreationCountry should be(Some("BE"))
      }
    }

    Scenario("User registered on a given location") {
      val source = readEvents("events/user-with-location")

      val resolver = new QuestionResolver(Nil, Map())

      val userWithLocation = user.copy(uuid = UserId("user-with-location"))

      when(userJournal.currentEventsByPersistenceId(eqTo(userWithLocation.uuid.value), any[Long], any[Long]))
        .thenReturn(source)

      whenReady(crmService.getPropertiesFromUser(userWithLocation, resolver), Timeout(5.seconds)) { result =>
        result.accountCreationLocation should contain("search-page")
      }
    }
  }

  Feature("createCrmUsers") {
    Scenario("single user") {

      val source = readEvents("events/user-50b3d4f6-4bfe-4102-94b1-bfcfdf12ef74")

      val operationId = OperationId("a818ef52-cd54-4aa7-bd3d-67e7bf4c4ea5")

      val question = Question(
        questionId = QuestionId("7d2ba29b-d503-44b8-98a0-5b9ae8b8bc69"),
        slug = "my-question",
        countries = NonEmptyList.of(Country("FR")),
        language = Language("fr"),
        question = "Comment sauver le monde ?",
        shortTitle = None,
        operationId = Some(operationId)
      )

      when(operationService.findSimple()).thenReturn(
        Future.successful(
          Seq(
            SimpleOperation(
              operationId,
              OperationStatus.Pending,
              question.slug,
              OperationKind.PrivateConsultation,
              None,
              None
            )
          )
        )
      )

      when(questionService.searchQuestion(SearchQuestionRequest())).thenReturn(Future.successful(Seq(question)))

      when(userJournal.currentEventsByPersistenceId(eqTo(user.uuid.value), any[Long], any[Long]))
        .thenReturn(source)

      when(persistentCrmUserService.truncateCrmUsers()).thenReturn(Future.unit)

      when(
        persistentCrmSynchroUserService.findUsersForCrmSynchro(None, None, 0, mailJetConfiguration.userListBatchSize)
      ).thenReturn(Future.successful(Seq(user)))

      when(
        persistentCrmSynchroUserService.findUsersForCrmSynchro(None, None, 1, mailJetConfiguration.userListBatchSize)
      ).thenReturn(Future.successful(Seq()))

      when(persistentCrmUserService.persist(any[Seq[PersistentCrmUser]])).thenAnswer { users: Seq[PersistentCrmUser] =>
        if (users.forall(_.operationActivity.contains(question.slug))) {
          Future.successful(users)
        } else {
          val failedUsers = users.filter(!_.operationActivity.contains(question.slug))
          failedUsers.foreach(
            user => logger.error(s"user ${user.userId} has operation ${user.operationActivity}, failing test")
          )
          fail()
        }
      }

      val result = crmService.createCrmUsers()
      whenReady(result, Timeout(2.seconds)) { _ =>
        verify(persistentCrmUserService).persist(any[Seq[PersistentCrmUser]])
      }

    }
  }

  Feature("question resolver") {
    Scenario("empty resolver") {
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

    Scenario("get by id") {
      val questionId = QuestionId("my-question")
      val question =
        Question(
          questionId = questionId,
          slug = "my-question",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "?",
          shortTitle = None,
          operationId = Some(OperationId("toto"))
        )
      val resolver = new QuestionResolver(Seq(question), Map.empty)

      val result = resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(questionId = Some(questionId))
      )

      result should contain(question)

      resolver.extractQuestionWithOperationFromRequestContext(
        RequestContext.empty.copy(questionId = Some(QuestionId("unknown")))
      ) should be(None)
    }

    Scenario("get by operation") {
      val questionId = QuestionId("my-question")
      val question =
        Question(
          questionId = questionId,
          slug = "my-question",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "?",
          shortTitle = None,
          operationId = Some(OperationId("my-operation"))
        )
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
    Scenario("get by operation slug") {
      val questionId = QuestionId("my-question")
      val question =
        Question(
          questionId = questionId,
          slug = "my-question",
          countries = NonEmptyList.of(Country("FR")),
          language = Language("fr"),
          question = "?",
          shortTitle = None,
          operationId = Some(OperationId("my-operation"))
        )
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

  Feature("synchronizing list") {

    Scenario("no user to synchronize") {
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

    Scenario("multiple users") {
      when(persistentCrmUserService.list(eqTo(Some(true)), eqTo(false), any[Int], any[Int]))
        .thenReturn(
          Future.successful(Seq(persistentCrmUser("1"), persistentCrmUser("2"))),
          Future.successful(Seq(persistentCrmUser("3"), persistentCrmUser("4"))),
          Future.successful(Seq(persistentCrmUser("5"), persistentCrmUser("6"))),
          Future.successful(Seq.empty)
        )

      when(crmClient.sendCsv(any[String], any[Path], any[Account])(any[ExecutionContext])).thenReturn(
        Future.successful(SendCsvResponse(1L)),
        Future.successful(SendCsvResponse(2L)),
        Future.successful(SendCsvResponse(3L)),
        Future.successful(SendCsvResponse(4L)),
        Future.successful(SendCsvResponse(5L)),
        Future.successful(SendCsvResponse(6L))
      )

      def csvImportResponse(
        jobId: Long,
        dataId: Long,
        errorCount: Int,
        status: String
      ): BasicCrmResponse[CsvImportResponse] = {
        BasicCrmResponse[CsvImportResponse](
          count = 1,
          total = 1,
          data = Seq(CsvImportResponse(jobId = jobId, dataId = dataId, errorCount = errorCount, status = status))
        )
      }

      when(crmClient.manageContactListWithCsv(any[CsvImport], any[Account])(any[ExecutionContext])).thenAnswer {
        (request: CsvImport) =>
          Future.successful(csvImportResponse(request.csvId.toLong * 10, request.csvId.toLong, 0, "Pending"))
      }

      when(crmClient.monitorCsvImport(eqTo(10L), any[Account])(any[ExecutionContext]))
        .thenReturn(
          Future.successful(csvImportResponse(10L, 1L, 0, "Pending")),
          Future.successful(csvImportResponse(10L, 1L, 0, "Pending")),
          Future.successful(csvImportResponse(10L, 1L, 0, "Pending")),
          Future.successful(csvImportResponse(10L, 1L, 0, "Pending")),
          Future.successful(csvImportResponse(10L, 1L, 0, "Completed"))
        )

      when(crmClient.monitorCsvImport(eqTo(20L), any[Account])(any[ExecutionContext]))
        .thenReturn(
          Future.successful(csvImportResponse(20L, 2L, 0, "Pending")),
          Future.successful(csvImportResponse(20L, 2L, 0, "Pending")),
          Future.successful(csvImportResponse(20L, 2L, 0, "Pending")),
          Future.successful(csvImportResponse(20L, 2L, 0, "Pending")),
          Future.successful(csvImportResponse(20L, 2L, 0, "Completed"))
        )

      when(crmClient.monitorCsvImport(eqTo(30L), any[Account])(any[ExecutionContext]))
        .thenReturn(
          Future.successful(csvImportResponse(30L, 3L, 0, "Pending")),
          Future.successful(csvImportResponse(30L, 3L, 0, "Pending")),
          Future.successful(csvImportResponse(30L, 3L, 0, "Pending")),
          Future.successful(csvImportResponse(30L, 3L, 0, "Pending")),
          Future.successful(csvImportResponse(30L, 3L, 0, "Completed"))
        )

      when(crmClient.monitorCsvImport(eqTo(40L), any[Account])(any[ExecutionContext]))
        .thenReturn(
          Future.failed(new IllegalStateException("Don't worry, this exception is fake")),
          Future.successful(csvImportResponse(40L, 4L, 0, "Pending")),
          Future.successful(csvImportResponse(40L, 4L, 0, "Pending")),
          Future.successful(csvImportResponse(40L, 4L, 0, "Pending")),
          Future.successful(csvImportResponse(40L, 4L, 0, "Completed"))
        )

      when(crmClient.monitorCsvImport(eqTo(50L), any[Account])(any[ExecutionContext]))
        .thenReturn(
          Future.successful(csvImportResponse(50L, 5L, 0, "Pending")),
          Future.successful(csvImportResponse(50L, 5L, 0, "Pending")),
          Future.successful(csvImportResponse(50L, 5L, 0, "Pending")),
          Future.successful(csvImportResponse(50L, 5L, 0, "Pending")),
          Future.successful(csvImportResponse(50L, 5L, 2, "Completed"))
        )

      when(crmClient.monitorCsvImport(eqTo(60L), any[Account])(any[ExecutionContext]))
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
        verify(crmClient, times(30))
          .monitorCsvImport(any[Long], any[Account])(any[ExecutionContext])
      }
    }
  }

  Feature("create csv") {
    Scenario("create csv from contact") {
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
            accountCreationLocation = Some("search-page"),
            countriesActivity = Some("FR"),
            lastCountryActivity = Some("FR"),
            totalProposals = Some(42),
            totalVotes = Some(1337),
            firstContributionDate = Some("2019-04-15 15:24:17"),
            lastContributionDate = Some("2019-10-07 10:47:42"),
            operationActivity = None,
            sourceActivity = None,
            daysOfActivity = None,
            daysOfActivity30 = None,
            userType = None,
            accountType = None,
            updatedAt = Some("2019-10-06 02:00:00"),
            daysBeforeDeletion = None,
            lastActivityDate = None,
            sessionsCount = None,
            eventsCount = None
          )
        )
      )

      contact.toStringCsv should be(
        s""""test@exemple.com","user","test",,"1992-01-01 00:00:00","true","false","false","2019-10-07 10:45:10",,,,"FR","search-page","FR","FR","42","1337","2019-04-15 15:24:17","2019-10-07 10:47:42",,,,,,,"2019-10-06 02:00:00",,,,${String
          .format("%n")}"""
      )
    }
  }

  Feature("create csv stream") {
    Scenario("create one csv") {
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
      val destination = Files.createTempDirectory("create-one-csv")
      destination.toFile.deleteOnExit()

      whenReady(crmService.createCsv(formattedDate, CrmList.OptIn, destination), Timeout(30.seconds)) { fileList =>
        val file = fileList.head
        val bufferedFile = IOSource.fromFile(file.toFile)
        val fileToSeq = bufferedFile.getLines().toSeq
        val lineCount = fileToSeq.size
        val firstLine = fileToSeq.head
        lineCount should be(2)
        firstLine should be(
          s""""foo@example.com","user-id","Foo",,,"true","false","false",,,,,,,,"FR","42","1337",,,,,,,,,"$formattedDate",,,,"""
        )
        bufferedFile.close()
      }
    }

    Scenario("split into 2 csv") {

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
      val destination = Files.createTempDirectory("split-into-2-csv")
      destination.toFile.deleteOnExit()

      whenReady(crmService.createCsv(formattedDate, CrmList.OptIn, destination), Timeout(30.seconds)) { fileList =>
        fileList.size should be > 1
      }
    }
  }

  Feature("Resend emails on error") {

    Scenario("sending email randomly fails") {
      forAll { (message: SendEmail, outcome: Either[Boolean, Unit]) =>
        Given("an unreliable email provider")
        when(crmClient.sendEmail(eqTo(SendMessages(message)), any[Account])(any[ExecutionContext]))
          .thenReturn(outcome match {
            case Left(clientError) =>
              Future.failed(
                CrmClientException.RequestException.SendEmailException(
                  if (clientError) StatusCodes.BadRequest else StatusCodes.InternalServerError,
                  "Don't worry, this exception is fake"
                )
              )
            case Right(_) => Future.successful(SendEmailResponse(Nil))
          })
        When("sending an email")
        crmService.sendEmail(message)
        Then("the message should be rescheduled if sending failed")
        verify(eventBusService, new After(1000, times(outcome match {
          case Left(false) => 1
          case _           => 0
        }))).publish(eqTo(message))
      }
    }

  }

}

object CrmServiceComponentTest {
  val configuration: String =
    """
      |akka.log-dead-letters-during-shutdown = off
      |make-api.security.secure-hash-salt = "salt-secure"
      |make-api.security.secure-vote-salt = "vote-secure"
      |make-api.security.aes-secret-key = "secret-key"
      |""".stripMargin
}

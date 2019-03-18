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

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

import akka.actor.ActorSystem
import akka.persistence.query.scaladsl.{CurrentEventsByPersistenceIdQuery, CurrentPersistenceIdsQuery, ReadJournal}
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.scaladsl
import org.make.api.extensions.{MailJetConfiguration, MailJetConfigurationComponent}
import org.make.api.operation.{OperationService, OperationServiceComponent}
import org.make.api.technical.ReadJournalComponent
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.userhistory._
import org.make.api.{ActorSystemComponent, MakeUnitTest}
import org.make.core.history.HistoryActions.Trusted
import org.make.core.operation.{Operation, OperationId, OperationStatus}
import org.make.core.profile.{Gender, Profile, SocioProfessionalCategory}
import org.make.core.proposal.{ProposalId, ProposalVoteAction, VoteKey}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.user.{Role, User, UserId}
import org.make.core.{DateHelper, RequestContext}
import org.mockito.ArgumentMatchers.{any, eq => matches}
import org.mockito.Mockito.when
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class CrmServiceComponentTest
    extends MakeUnitTest
    with DefaultCrmServiceComponent
    with OperationServiceComponent
    with MailJetConfigurationComponent
    with ActorSystemComponent
    with UserHistoryCoordinatorServiceComponent
    with ReadJournalComponent {

  trait MakeReadJournalForMocks
      extends ReadJournal
      with CurrentPersistenceIdsQuery
      with CurrentEventsByPersistenceIdQuery

  override lazy val actorSystem: ActorSystem = ActorSystem()
  override val userHistoryCoordinatorService: UserHistoryCoordinatorService = mock[UserHistoryCoordinatorService]
  override val proposalJournal: MakeReadJournal = mock[MakeReadJournalForMocks]
  override val userJournal: MakeReadJournal = mock[MakeReadJournalForMocks]
  override val sessionJournal: MakeReadJournal = mock[MakeReadJournalForMocks]
  override val mailJetConfiguration: MailJetConfiguration = mock[MailJetConfiguration]
  override val operationService: OperationService = mock[OperationService]

  val zonedDateTimeInThePast: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")
  val zonedDateTimeInThePastAt31daysBefore: ZonedDateTime = DateHelper.now().minusDays(31)
  val zonedDateTimeNow: ZonedDateTime = DateHelper.now()

  when(mailJetConfiguration.url).thenReturn("")

  val defaultOperation: Operation = Operation(
    status = OperationStatus.Active,
    operationId = OperationId("default"),
    slug = "default",
    defaultLanguage = Language("fr"),
    allowedSources = Seq.empty,
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
        arguments = UserVote(proposalId = ProposalId("proposalId"), voteKey = VoteKey.Neutral, trust = Trusted)
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
        arguments = UserVote(proposalId = ProposalId("proposalId"), voteKey = VoteKey.Agree, trust = Trusted)
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
        arguments = UserVote(proposalId = ProposalId("proposalId"), voteKey = VoteKey.Agree, trust = Trusted)
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
        arguments = UserVote(proposalId = ProposalId("proposalId"), voteKey = VoteKey.Agree, trust = Trusted)
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
      val futureProperties = crmService.getPropertiesFromUser(userWithoutRegisteredEvent)
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
        maybeProperties.accountCreationOperation shouldBe None
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
        maybeProperties.numberOfThemes shouldBe Some(0)
        maybeProperties.userType shouldBe Some("B2C")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(maybeProperties.updatedAt.getOrElse(""))
        updatedAt.isBefore(DateHelper.now()) && updatedAt.isAfter(DateHelper.now().minusSeconds(10)) shouldBe (true)
      }
    }

    scenario("users properties are not normalized after date fix") {
      Given("a registered user without register event and with a recent creation date")
      When("I get user properties")

      val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
      val futureProperties = crmService.getPropertiesFromUser(userWithoutRegisteredEventAfterDateFix)
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
        maybeProperties.accountCreationOperation shouldBe None
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
        maybeProperties.numberOfThemes shouldBe Some(0)
        maybeProperties.userType shouldBe Some("B2C")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(maybeProperties.updatedAt.getOrElse(""))
        updatedAt.isBefore(DateHelper.now()) && updatedAt.isAfter(DateHelper.now().minusSeconds(10)) shouldBe (true)
      }
    }
  }

  feature("add user to OptInList") {
    scenario("get properties from user and his events") {
      Given("a registred user")

      When("I add a user into optInList")
      Then("The properties are calculated from UserHistory")

      val futureProperties = crmService.getPropertiesFromUser(fooUser)
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
        maybeProperties.accountCreationOperation shouldBe Some("999-99-99")
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
        maybeProperties.operationActivity shouldBe Some("999-99-99,200-20-11,345-34-89,invalidoperation")
        maybeProperties.activeCore shouldBe Some(true)
        maybeProperties.daysOfActivity shouldBe Some(3)
        maybeProperties.daysOfActivity30 shouldBe Some(1)
        maybeProperties.numberOfThemes shouldBe Some(1)
        maybeProperties.userType shouldBe Some("B2C")
        val updatedAt: ZonedDateTime = ZonedDateTime.parse(maybeProperties.updatedAt.getOrElse(""))
        updatedAt.isBefore(DateHelper.now()) && updatedAt.isAfter(DateHelper.now().minusSeconds(10)) shouldBe (true)
      }
    }
  }
}

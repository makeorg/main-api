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

import java.time.{LocalDate, ZonedDateTime}

import akka.actor.ActorSystem
import akka.persistence.query.scaladsl.{CurrentEventsByPersistenceIdQuery, CurrentPersistenceIdsQuery, ReadJournal}
import akka.persistence.query.{EventEnvelope, Offset}
import akka.stream.scaladsl
import org.make.api.{ActorSystemComponent, MakeUnitTest}
import org.make.api.extensions.{MailJetConfiguration, MailJetConfigurationComponent}
import org.make.api.technical.ReadJournalComponent
import org.make.api.technical.ReadJournalComponent.MakeReadJournal
import org.make.api.userhistory._
import org.make.core.RequestContext
import org.make.core.operation.OperationId
import org.make.core.profile.{Gender, Profile}
import org.make.core.proposal.{ProposalId, ProposalVoteAction, VoteKey}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.user.{Role, User, UserId}
import org.mockito.Mockito.when
import org.mockito.ArgumentMatchers.any
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt

class CrmServiceComponentTest
    extends MakeUnitTest
    with DefaultCrmServiceComponent
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
  override val readJournal: MakeReadJournal = mock[MakeReadJournalForMocks]
  override val mailJetConfiguration: MailJetConfiguration = mock[MailJetConfiguration]

  val zonedDateTimeInThePast: ZonedDateTime = ZonedDateTime.parse("2017-06-01T12:30:40Z[UTC]")

  val fooProfile = Profile(
    dateOfBirth = Some(LocalDate.parse("2000-01-01")),
    avatarUrl = Some("https://www.example.com"),
    profession = Some("profession"),
    phoneNumber = Some("010101"),
    twitterId = Some("@twitterid"),
    facebookId = Some("facebookid"),
    googleId = Some("googleId"),
    gender = Some(Gender.Male),
    genderName = Some("other"),
    postalCode = Some("93"),
    karmaLevel = Some(2),
    locale = Some("fr_FR")
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
    createdAt = Some(zonedDateTimeInThePast)
  )

  val registerCitizenEventEnvelope = EventEnvelope(
    offset = Offset.noOffset,
    persistenceId = "foo-persistance-id",
    sequenceNr = Long.MaxValue,
    event = LogRegisterCitizenEvent(
      userId = UserId("1"),
      requestContext = RequestContext.empty.copy(
        source = Some("core"),
        operationId = Some(OperationId("culture")),
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

  val userProposalEventEnvolope = EventEnvelope(
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
        arguments = UserVote(proposalId = ProposalId("proposalId"), voteKey = VoteKey.Neutral)
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
        arguments = UserVote(proposalId = ProposalId("proposalId"), voteKey = VoteKey.Agree)
      )
    )
  )

  when(readJournal.currentEventsByPersistenceId(any[String], any[Long], any[Long]))
    .thenReturn(
      scaladsl.Source(
        List(registerCitizenEventEnvelope, userProposalEventEnvolope, userVoteEventEnvelope, userVoteEventEnvelope2)
      )
    )

  feature("add user to OptInList") {
    scenario("get properties from user and his events") {
      Given("a registred user")

      When("I add a user into optInList")
      Then("The properties are calculated from UserHistory")

      val futureProperties = crmService.getPropertiesFromUser(fooUser)
      whenReady(futureProperties, Timeout(3.seconds)) { maybeProperties =>
        maybeProperties.get("UserId") shouldBe Some("1")
        maybeProperties.get("Firstname") shouldBe Some("Foo")
        maybeProperties.get("Zipcode") shouldBe Some("93")
        maybeProperties.get("Date_Of_Birth") shouldBe Some("2000-01-01")
        maybeProperties.get("Email_Validation_Status") shouldBe Some("true")
        maybeProperties.get("Email_Hardbounce_Status") shouldBe Some("false")
        maybeProperties.get("Unsubscribe_Status") shouldBe Some("false")
        maybeProperties.get("Account_Creation_Date") shouldBe Some("01/06/2017 - 12:30")
        maybeProperties.get("Account_creation_source") shouldBe Some("core")
        maybeProperties.get("Account_Creation_Operation") shouldBe Some("culture")
        maybeProperties.get("Account_Creation_Country") shouldBe Some("FR")
        maybeProperties.get("Countries_activity") shouldBe Some("FR,IT,GB")
        maybeProperties.get("Last_country_activity") shouldBe Some("FR")
        maybeProperties.get("Last_language_activity") shouldBe Some("fr")
        maybeProperties.get("Total_Number_Proposals") shouldBe Some("1")
        maybeProperties.get("Total_number_votes") shouldBe Some("2")
        maybeProperties.get("First_Contribution_Date") shouldBe Some("01/06/2017 - 12:30")
        maybeProperties.get("Last_Contribution_Date") shouldBe Some("01/06/2017 - 12:30")
        maybeProperties.get("Operation_activity") shouldBe Some("culture,vff")
        maybeProperties.get("Active_core") shouldBe Some("true")
        maybeProperties.get("Days_of_Activity") shouldBe Some("1")
        maybeProperties.get("Days_of_Activity_30d") shouldBe Some("0")
        maybeProperties.get("Number_of_themes") shouldBe Some("1")
        maybeProperties.get("User_type") shouldBe Some("B2C")
      }
    }
  }
}

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

package org.make.api.userhistory

import java.time.{LocalDate, ZonedDateTime}

import org.make.api.proposal.PublishedProposalEvent.{
  ProposalAccepted,
  ProposalLocked,
  ProposalPostponed,
  ProposalRefused
}
import org.make.api.userhistory
import org.make.api.userhistory.UserHistoryActor.{UserHistory, UserVotesAndQualifications}
import org.make.core.RequestContext
import org.make.core.history.HistoryActions.VoteAndQualifications
import org.make.core.history.HistoryActions.VoteTrust.Trusted
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.proposal.ProposalActionType._
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.sequence.{SearchQuery, SequenceId, SequenceStatus}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.scalatest.wordspec.AnyWordSpec
import stamina.testkit.StaminaTestKit
import stamina.{Persisters, V1, V2}

class UserHistorySerializersTest extends AnyWordSpec with StaminaTestKit {

  val persisters = Persisters(UserHistorySerializers.serializers.toList)
  val userId = UserId("my-user-id")
  val requestContext: RequestContext =
    RequestContext.empty
  val eventDate: ZonedDateTime = ZonedDateTime.parse("2018-03-01T16:09:30.441Z")
  val recentDate: ZonedDateTime = ZonedDateTime.parse("2018-09-02T16:09:30.441Z")

  "user history persister" should {
    val userAddProposalsSequenceEvent = LogUserAddProposalsSequenceEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = SequenceProposalsAdded.actionType,
        arguments = SequenceProposalsAdded(
          id = SequenceId("sequence-id"),
          proposalIds = Seq(ProposalId("proposalId")),
          requestContext = requestContext,
          eventDate = eventDate,
          userId = userId
        )
      )
    )
    val userUnvoteEvent = LogUserUnvoteEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = ProposalUnvoteAction.value,
        arguments = UserUnvote(proposalId = ProposalId("proposal-id"), voteKey = VoteKey.Neutral, trust = Trusted)
      )
    )
    val userStartSequenceEvent = LogUserStartSequenceEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = LogUserStartSequenceEvent.action,
        arguments = StartSequenceParameters(slug = Some("vff"), questionId = None, sequenceId = Some(SequenceId("vff")))
      )
    )
    val searchProposalsEvent = LogUserSearchProposalsEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = LogUserSearchProposalsEvent.action,
        arguments = UserSearchParameters(term = "il faut caliner les cacahuettes")
      )
    )
    val acceptProposalEvent = LogAcceptProposalEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = ProposalAccepted.actionType,
        arguments = ProposalAccepted(
          id = ProposalId("proposal-id"),
          eventDate = eventDate,
          requestContext = requestContext,
          moderator = userId,
          edition = None,
          sendValidationEmail = false,
          theme = Some(ThemeId("123-456-789")),
          labels = Seq(LabelId("label")),
          tags = Seq(TagId("police"), TagId("justice")),
          similarProposals = Seq(ProposalId("proposal1"), ProposalId("proposal2")),
          idea = Some(IdeaId("my-idea")),
          operation = Some(OperationId("my-operation"))
        )
      )
    )

    val userQualificationEvent = LogUserQualificationEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = ProposalQualifyAction.value,
        arguments = UserQualification(
          proposalId = ProposalId("proposal-id"),
          qualificationKey = QualificationKey.Doable,
          trust = Trusted
        )
      )
    )

    val postponeProposalEvent = LogPostponeProposalEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = ProposalPostponed.actionType,
        arguments = ProposalPostponed(
          id = ProposalId("proposal-id"),
          eventDate = eventDate,
          requestContext = requestContext,
          moderator = userId
        )
      )
    )

    val userRemoveProposalsSequenceEvent = LogUserRemoveProposalsSequenceEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = SequenceProposalsRemoved.actionType,
        arguments = SequenceProposalsRemoved(
          id = SequenceId("sequence-id"),
          proposalIds = Seq(ProposalId("proposal-1"), ProposalId("proposal-2")),
          requestContext = requestContext,
          eventDate = eventDate,
          userId = userId
        )
      )
    )

    val userSearchSequencesEvent = LogUserSearchSequencesEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = LogUserSearchSequencesEvent.action,
        arguments = SearchSequenceParameters(query = SearchQuery()) // TODO: have parameters here
      )
    )

    val userVoteEvent = LogUserVoteEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = ProposalVoteAction.value,
        arguments = UserVote(proposalId = ProposalId("proposalId"), voteKey = VoteKey.Neutral, trust = Trusted)
      )
    )

    val getProposalDuplicatesEvent = LogGetProposalDuplicatesEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = LogGetProposalDuplicatesEvent.action,
        arguments = ProposalId("proposal-id")
      )
    )

    val refuseProposalEvent = LogRefuseProposalEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = ProposalRefused.actionType,
        arguments = ProposalRefused(
          id = ProposalId("proposal-id"),
          eventDate = eventDate,
          requestContext = requestContext,
          moderator = userId,
          sendRefuseEmail = false,
          refusalReason = Some("because"),
          operation = Some(OperationId("operation-id"))
        )
      )
    )

    val userUnqualificationEvent = LogUserUnqualificationEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = ProposalUnqualifyAction.value,
        arguments = UserUnqualification(
          proposalId = ProposalId("proposal-id"),
          qualificationKey = QualificationKey.LikeIt,
          trust = Trusted
        )
      )
    )

    val userProposalEvent = LogUserProposalEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = LogUserProposalEvent.action,
        arguments = UserProposal(content = "il faut proposer", theme = Some(ThemeId("my-theme")))
      )
    )

    val userUpdateSequenceEvent = LogUserUpdateSequenceEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = SequenceUpdated.actionType,
        arguments = SequenceUpdated(
          id = SequenceId("sequence-id"),
          userId = userId,
          eventDate = eventDate,
          requestContext = requestContext,
          title = Some("sequence title"),
          status = Some(SequenceStatus.Published),
          operationId = Some(OperationId("operation-id")),
          operation = Some("my-operation-slug"),
          themeIds = Seq(ThemeId("theme-1"), ThemeId("theme2"))
        )
      )
    )

    val registerCitizenEvent = LogRegisterCitizenEvent(
      userId = userId,
      requestContext =
        requestContext.copy(source = Some("core"), language = Some(Language("fr")), country = Some(Country("FR"))),
      action = UserAction(
        date = eventDate,
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

    val registerCitizenEventIt = LogRegisterCitizenEvent(
      userId = userId,
      requestContext =
        requestContext.copy(source = Some("core"), language = Some(Language("it")), country = Some(Country("IT"))),
      action = UserAction(
        date = eventDate,
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

    val recentRegisterCitizenEvent = LogRegisterCitizenEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = recentDate,
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

    val lockProposalEvent = LogLockProposalEvent(
      userId = userId,
      moderatorName = Some("moderator name"),
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = ProposalLocked.actionType,
        arguments = ProposalLocked(
          id = ProposalId("proposal-id"),
          moderatorId = userId,
          moderatorName = Some("moderator name"),
          eventDate = eventDate,
          requestContext = requestContext
        )
      )
    )

    val userCreateSequenceEvent = LogUserCreateSequenceEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = SequenceCreated.actionType,
        arguments = SequenceCreated(
          id = SequenceId("sequence-id"),
          slug = "my-sequence",
          requestContext = requestContext,
          userId = userId,
          eventDate = eventDate,
          title = "my sequence",
          themeIds = Seq(ThemeId("theme-1"), ThemeId("theme-2")),
          operationId = Some(OperationId("operation-id")),
          searchable = true
        )
      )
    )

    val userAnonymizedEvent = LogUserAnonymizedEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = UserAnonymized.actionType,
        arguments = UserAnonymized(userId = UserId("anoned"), adminId = UserId("admin"))
      )
    )

    val userOptInNewsletterEvent = LogUserOptInNewsletterEvent(
      userId = userId,
      requestContext = requestContext,
      action =
        UserAction(date = eventDate, actionType = UserUpdatedOptIn.actionType, arguments = UserUpdatedOptIn(true))
    )

    val userOptOutNewsletterEvent = LogUserOptOutNewsletterEvent(
      userId = userId,
      requestContext = requestContext,
      action =
        UserAction(date = eventDate, actionType = UserUpdatedOptIn.actionType, arguments = UserUpdatedOptIn(false))
    )

    val userUserConnectedEvent = LogUserConnectedEvent(
      userId = userId,
      requestContext = requestContext,
      action = UserAction(
        date = eventDate,
        actionType = UserHasConnected.actionType,
        arguments = userhistory.UserHasConnected()
      )
    )

    val defaultDate = ZonedDateTime.parse("2018-10-10T00:00:00Z")

    val userVotesAndQualifications = UserVotesAndQualifications(
      Map(
        ProposalId("some-proposal") -> VoteAndQualifications(
          VoteKey.Agree,
          Map(QualificationKey.LikeIt -> Trusted, QualificationKey.Doable -> Trusted),
          defaultDate,
          Trusted
        )
      )
    )

    val multipleVoteAndQualifications =
      UserVotesAndQualifications(
        Map(
          ProposalId("df9c3bac-ca5d-43dc-87fb-c4980c711297") -> VoteAndQualifications(
            VoteKey.Neutral,
            Map.empty,
            defaultDate,
            Trusted
          ),
          ProposalId("9c2dfbcd-1a52-4337-9378-47c5ef1e94a6") -> VoteAndQualifications(
            VoteKey.Neutral,
            Map.empty,
            defaultDate,
            Trusted
          ),
          ProposalId("17f40d57-f510-4049-bb1a-9637193107d6") -> VoteAndQualifications(
            VoteKey.Neutral,
            Map.empty,
            defaultDate,
            Trusted
          ),
          ProposalId("857a9689-9e97-4811-b18e-3f4e8f993b0d") -> VoteAndQualifications(
            VoteKey.Neutral,
            Map.empty,
            defaultDate,
            Trusted
          )
        )
      )

    persisters.generateTestsFor(
      sample(userAddProposalsSequenceEvent),
      sample(userUnvoteEvent),
      sample(userStartSequenceEvent),
      sample(searchProposalsEvent),
      sample(acceptProposalEvent),
      sample(userQualificationEvent),
      sample(postponeProposalEvent),
      sample(userRemoveProposalsSequenceEvent),
      sample(userSearchSequencesEvent),
      sample(userVoteEvent),
      sample(getProposalDuplicatesEvent),
      sample(refuseProposalEvent),
      sample(userUnqualificationEvent),
      sample(userProposalEvent),
      sample(userUpdateSequenceEvent),
      sample(registerCitizenEvent),
      sample(lockProposalEvent),
      sample(userCreateSequenceEvent),
      PersistableSample[V2]("userVotesAndQualification", userVotesAndQualifications, None),
      PersistableSample[V2](
        "recent",
        recentRegisterCitizenEvent,
        Some("to validate source migration with recent event")
      ),
      PersistableSample[V1](
        "multiple",
        multipleVoteAndQualifications,
        Some("to validate source migration with old votes and multiple proposals")
      ),
      sample(userAnonymizedEvent),
      sample(userOptInNewsletterEvent),
      sample(userOptOutNewsletterEvent),
      sample(userUserConnectedEvent),
      sample(
        UserHistory(
          List(
            userAddProposalsSequenceEvent,
            userUnvoteEvent,
            userStartSequenceEvent,
            searchProposalsEvent,
            acceptProposalEvent,
            userQualificationEvent,
            postponeProposalEvent,
            userRemoveProposalsSequenceEvent,
            userSearchSequencesEvent,
            userVoteEvent,
            getProposalDuplicatesEvent,
            refuseProposalEvent,
            userUnqualificationEvent,
            userProposalEvent,
            userUpdateSequenceEvent,
            registerCitizenEvent,
            lockProposalEvent,
            userCreateSequenceEvent
          )
        )
      ),
      PersistableSample[V1](
        "it",
        UserHistory(
          List(
            userAddProposalsSequenceEvent,
            userUnvoteEvent,
            userStartSequenceEvent,
            searchProposalsEvent,
            acceptProposalEvent,
            userQualificationEvent,
            postponeProposalEvent,
            userRemoveProposalsSequenceEvent,
            userSearchSequencesEvent,
            userVoteEvent,
            getProposalDuplicatesEvent,
            refuseProposalEvent,
            userUnqualificationEvent,
            userProposalEvent,
            userUpdateSequenceEvent,
            registerCitizenEventIt,
            lockProposalEvent,
            userCreateSequenceEvent
          )
        ),
        Some("italian user history")
      )
    )
  }
}

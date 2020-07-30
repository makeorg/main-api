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

package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.api.proposal.ProposalActor.{Lock, ProposalState}
import org.make.api.proposal.ProposalEvent.{SimilarProposalRemoved, SimilarProposalsCleared}
import org.make.api.proposal.PublishedProposalEvent._
import org.make.core.RequestContext
import org.make.core.history.HistoryActions.VoteTrust.Trusted
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.QualificationKey.{
  DoNotCare,
  DoNotUnderstand,
  Doable,
  Impossible,
  LikeIt,
  NoOpinion,
  NoWay,
  PlatitudeAgree,
  PlatitudeDisagree
}
import org.make.core.proposal.VoteKey.{Agree, Disagree, Neutral}
import org.make.core.proposal._
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.scalatest.wordspec.AnyWordSpec
import stamina.testkit.StaminaTestKit
import stamina.{Persisters, V3, V8}

class ProposalSerializersTest extends AnyWordSpec with StaminaTestKit {

  val persisters = Persisters(ProposalSerializers.serializers.toList)
  val userId = UserId("my-user-id")
  val requestContext: RequestContext = RequestContext.empty
  val requestContextFromGermany: RequestContext =
    requestContext.copy(country = Some(Country("DE")), language = Some(Language("de")))
  val eventDate: ZonedDateTime = ZonedDateTime.parse("2018-03-01T16:09:30.441Z")
  val proposalId = ProposalId("proposal-id")

  "proposal persister" should {

    val proposalProposed = ProposalProposed(
      id = proposalId,
      slug = "my-proposal",
      requestContext = requestContext,
      author =
        ProposalAuthorInfo(userId = userId, firstName = Some("first name"), postalCode = Some("75011"), age = Some(42)),
      userId = userId,
      eventDate = eventDate,
      content = "my proposal",
      operation = Some(OperationId("my-operation")),
      theme = Some(ThemeId("theme-id")),
      language = Some(Language("fr")),
      country = Some(Country("FR"))
    )

    val proposalViewed = ProposalViewed(id = proposalId, eventDate = eventDate, requestContext = requestContext)

    val proposalUpdated = ProposalUpdated(
      id = proposalId,
      eventDate = eventDate,
      requestContext = requestContext,
      updatedAt = eventDate,
      moderator = Some(userId),
      content = "new content",
      edition = Some(ProposalEdition(oldVersion = "old version", newVersion = "new version")),
      theme = Some(ThemeId("theme-id")),
      labels = Seq(LabelId("my-label")),
      tags = Seq(TagId("tag-1"), TagId("tag-2")),
      idea = Some(IdeaId("idea-id")),
      operation = Some(OperationId("operation-id")),
      similarProposals = Seq(ProposalId("proposal-1"), ProposalId("proposal-2"))
    )

    val proposalAccepted = ProposalAccepted(
      id = proposalId,
      eventDate = eventDate,
      requestContext = requestContext,
      moderator = userId,
      edition = Some(ProposalEdition(oldVersion = "old version", newVersion = "new version")),
      sendValidationEmail = true,
      theme = Some(ThemeId("theme-id")),
      labels = Seq(LabelId("label-id")),
      tags = Seq(TagId("tag-1"), TagId("tag-2")),
      similarProposals = Seq(ProposalId("proposal-1"), ProposalId("proposal-2")),
      idea = Some(IdeaId("idea-id")),
      operation = Some(OperationId("operation-id"))
    )

    val proposalRefused = ProposalRefused(
      id = proposalId,
      eventDate = eventDate,
      requestContext = requestContext,
      moderator = userId,
      sendRefuseEmail = true,
      refusalReason = Some("because"),
      operation = Some(OperationId("operation-id"))
    )

    val proposalPostponed =
      ProposalPostponed(id = proposalId, eventDate = eventDate, requestContext = requestContext, moderator = userId)

    val proposalVoted = ProposalVoted(
      id = proposalId,
      eventDate = eventDate,
      organisationInfo = None,
      maybeOrganisationId = None,
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Disagree,
      voteTrust = Trusted
    )

    val proposalVotedOrganisations = ProposalVoted(
      id = proposalId,
      eventDate = eventDate,
      organisationInfo = None,
      maybeOrganisationId = Some(UserId("my-user-id")),
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Disagree,
      voteTrust = Trusted
    )

    val proposalUnvoted = ProposalUnvoted(
      id = proposalId,
      eventDate = eventDate,
      organisationInfo = None,
      maybeOrganisationId = None,
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Agree,
      selectedQualifications = Seq(QualificationKey.LikeIt),
      voteTrust = Trusted
    )

    val proposalUnvotedOrganisations = ProposalUnvoted(
      id = proposalId,
      eventDate = eventDate,
      organisationInfo = None,
      maybeOrganisationId = Some(UserId("my-user-id")),
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Agree,
      selectedQualifications = Seq(QualificationKey.LikeIt),
      voteTrust = Trusted
    )

    val proposalQualified = ProposalQualified(
      id = proposalId,
      eventDate = eventDate,
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Agree,
      qualificationKey = QualificationKey.Doable,
      voteTrust = Trusted
    )

    val proposalUnqualified = ProposalUnqualified(
      id = proposalId,
      eventDate = eventDate,
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Neutral,
      qualificationKey = QualificationKey.NoOpinion,
      voteTrust = Trusted
    )

    val proposalLocked = ProposalLocked(
      id = proposalId,
      moderatorId = userId,
      moderatorName = Some("moderator name"),
      eventDate = eventDate,
      requestContext = requestContext
    )

    val similarProposalsAdded = SimilarProposalsAdded(
      id = proposalId,
      eventDate = eventDate,
      requestContext = requestContext,
      similarProposals = Set(ProposalId("similar-id"))
    )

    val similarProposalRemoved = SimilarProposalRemoved(
      id = proposalId,
      eventDate = eventDate,
      requestContext = requestContext,
      proposalToRemove = ProposalId("to-remove")
    )

    val similarProposalsCleared =
      SimilarProposalsCleared(id = proposalId, eventDate = eventDate, requestContext = requestContext)

    val proposalAddedToOperation =
      ProposalAddedToOperation(
        id = proposalId,
        eventDate = eventDate,
        requestContext = requestContext,
        operationId = OperationId("operation-id"),
        moderatorId = userId
      )

    val proposalRemovedFromOperation =
      ProposalRemovedFromOperation(
        id = proposalId,
        eventDate = eventDate,
        requestContext = requestContext,
        operationId = OperationId("operation-id"),
        moderatorId = userId
      )

    val proposalVotesUpdated = ProposalVotesUpdated(
      id = proposalId,
      eventDate = eventDate,
      requestContext = requestContext,
      updatedAt = eventDate,
      moderator = Some(userId),
      newVotes = Seq(
        Vote(
          key = Agree,
          count = 1,
          countVerified = 2,
          countSequence = 3,
          countSegment = 4,
          qualifications = Seq(
            Qualification(LikeIt, 101, 102, 103, 104),
            Qualification(Doable, 111, 112, 113, 114),
            Qualification(PlatitudeAgree, 121, 122, 123, 124)
          )
        ),
        Vote(
          key = Disagree,
          count = 11,
          countVerified = 12,
          countSequence = 13,
          countSegment = 14,
          qualifications = Seq(
            Qualification(NoWay, 201, 202, 203, 204),
            Qualification(Impossible, 211, 212, 213, 214),
            Qualification(PlatitudeDisagree, 221, 222, 223, 224)
          )
        ),
        Vote(
          key = Neutral,
          count = 21,
          countVerified = 22,
          countSequence = 23,
          countSegment = 24,
          qualifications = Seq(
            Qualification(DoNotUnderstand, 301, 302, 303, 304),
            Qualification(NoOpinion, 311, 312, 313, 314),
            Qualification(DoNotCare, 321, 322, 323, 324)
          )
        )
      )
    )

    val proposal = Proposal(
      proposalId = proposalId,
      slug = "my-proposal",
      content = "My proposal",
      author = userId,
      labels = Seq(LabelId("star")),
      theme = Some(ThemeId("theme-id")),
      status = ProposalStatus.Accepted,
      refusalReason = Some("because"),
      tags = Seq(TagId("tag-1"), TagId("tag-2")),
      creationContext = requestContext,
      idea = Some(IdeaId("idea-id")),
      operation = Some(OperationId("operation-id")),
      createdAt = Some(eventDate),
      updatedAt = Some(eventDate),
      votes = Seq(
        Vote(
          key = VoteKey.Agree,
          count = 20,
          countVerified = 20,
          countSequence = 20,
          countSegment = 0,
          qualifications = Seq(
            Qualification(
              key = QualificationKey.Doable,
              count = 12,
              countVerified = 12,
              countSequence = 12,
              countSegment = 0
            )
          )
        )
      ),
      events = List(
        ProposalAction(
          date = eventDate,
          user = userId,
          actionType = "qualification",
          arguments = Map("argument" -> "value")
        )
      )
    )
    val proposalFromGermany = proposal.copy(creationContext = requestContextFromGermany)

    val proposalPatched =
      ProposalPatched(id = proposalId, eventDate = eventDate, requestContext = requestContext, proposal = proposal)
    val proposalPatchedFromGermany =
      proposalPatched.copy(requestContext = requestContextFromGermany, proposal = proposalFromGermany)

    val proposalState = ProposalState(
      proposal = proposal,
      lock = Some(Lock(moderatorId = userId, moderatorName = "moderator name", expirationDate = eventDate))
    )

    persisters.generateTestsFor(
      sample(proposalProposed),
      sample(proposalViewed),
      sample(proposalUpdated),
      sample(proposalAccepted),
      sample(proposalRefused),
      sample(proposalPostponed),
      sample(proposalVoted),
      PersistableSample[V3]("organisations", proposalVotedOrganisations, Some("organisations")),
      sample(proposalUnvoted),
      PersistableSample[V3]("organisations", proposalUnvotedOrganisations, Some("organisations")),
      sample(proposalQualified),
      sample(proposalUnqualified),
      sample(proposalLocked),
      sample(similarProposalsAdded),
      sample(similarProposalRemoved),
      sample(similarProposalsCleared),
      sample(proposalAddedToOperation),
      sample(proposalRemovedFromOperation),
      sample(proposalPatched),
      PersistableSample[V8]("with-locale", proposalPatchedFromGermany, Some("with locale")),
      sample(proposalVotesUpdated),
      sample(proposalState)
    )

  }
}

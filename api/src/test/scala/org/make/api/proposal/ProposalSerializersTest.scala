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
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.scalatest.WordSpec
import stamina.{Persisters, V2}
import stamina.testkit.StaminaTestKit

class ProposalSerializersTest extends WordSpec with StaminaTestKit {

  val persisters = Persisters(ProposalSerializers.serializers.toList)
  val userId = UserId("my-user-id")
  val requestContext: RequestContext = RequestContext.empty
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
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Disagree
    )

    val proposalVotedOrganisations = ProposalVoted(
      id = proposalId,
      eventDate = eventDate,
      organisationInfo = Some(OrganisationInfo(UserId("my-user-id"), Some("make.org"))),
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Disagree
    )

    val proposalUnvoted = ProposalUnvoted(
      id = proposalId,
      eventDate = eventDate,
      organisationInfo = None,
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Agree,
      selectedQualifications = Seq(QualificationKey.LikeIt)
    )

    val proposalUnvotedOrganisations = ProposalUnvoted(
      id = proposalId,
      eventDate = eventDate,
      organisationInfo = Some(OrganisationInfo(UserId("my-user-id"), Some("make.org"))),
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Agree,
      selectedQualifications = Seq(QualificationKey.LikeIt)
    )

    val proposalQualified = ProposalQualified(
      id = proposalId,
      eventDate = eventDate,
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Agree,
      qualificationKey = QualificationKey.Doable
    )

    val proposalUnqualified = ProposalUnqualified(
      id = proposalId,
      eventDate = eventDate,
      requestContext = requestContext,
      maybeUserId = Some(userId),
      voteKey = VoteKey.Neutral,
      qualificationKey = QualificationKey.NoOpinion
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
      language = Some(Language("fr")),
      country = Some(Country("FR")),
      creationContext = requestContext,
      idea = Some(IdeaId("idea-id")),
      operation = Some(OperationId("operation-id")),
      createdAt = Some(eventDate),
      updatedAt = Some(eventDate),
      similarProposals = Seq(ProposalId("similar-1")),
      votes = Seq(
        Vote(
          key = VoteKey.Agree,
          count = 20,
          qualifications = Seq(Qualification(key = QualificationKey.Doable, count = 12))
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

    val proposalPatched =
      ProposalPatched(id = proposalId, eventDate = eventDate, requestContext = requestContext, proposal = proposal)

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
      PersistableSample[V2]("organisations", proposalVotedOrganisations, Some("organisations")),
      sample(proposalUnvoted),
      PersistableSample[V2]("organisations", proposalUnvotedOrganisations, Some("organisations")),
      sample(proposalQualified),
      sample(proposalUnqualified),
      sample(proposalLocked),
      sample(similarProposalsAdded),
      sample(similarProposalRemoved),
      sample(similarProposalsCleared),
      sample(proposalAddedToOperation),
      sample(proposalRemovedFromOperation),
      sample(proposalPatched),
      sample(proposalState)
    )

  }
}

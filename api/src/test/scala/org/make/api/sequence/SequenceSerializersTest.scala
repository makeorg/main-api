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

package org.make.api.sequence

import java.time.ZonedDateTime

import org.make.api.sequence.PublishedSequenceEvent._
import org.make.core.RequestContext
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.reference.{Language, ThemeId}
import org.make.core.sequence._
import org.make.core.user.UserId
import org.scalatest.WordSpec
import stamina.Persisters
import stamina.testkit.StaminaTestKit

class SequenceSerializersTest extends WordSpec with StaminaTestKit {

  val persisters = Persisters(SequenceSerializers.serializers.toList)
  val userId = UserId("my-user-id")
  val requestContext: RequestContext = RequestContext.empty
  val eventDate: ZonedDateTime = ZonedDateTime.parse("2018-03-01T16:09:30.441Z")

  "sequence persister" should {

    val sequenceId = SequenceId("sequence-id")
    val requestContext = RequestContext.empty
    val operationId = OperationId("operation-id")

    val sequenceCreated =
      SequenceCreated(
        id = sequenceId,
        requestContext = requestContext,
        slug = "my-sequence",
        userId = userId,
        eventDate = eventDate,
        title = "my sequence",
        themeIds = Seq(ThemeId("theme-id")),
        operationId = Some(operationId),
        searchable = true
      )

    val sequenceProposalsAdded =
      SequenceProposalsAdded(
        id = sequenceId,
        proposalIds = Seq(ProposalId("proposal-1"), ProposalId("proposal-2")),
        requestContext = requestContext,
        eventDate = eventDate,
        userId = userId
      )

    val sequenceProposalsRemoved = SequenceProposalsRemoved(
      id = sequenceId,
      proposalIds = Seq(ProposalId("proposal-1"), ProposalId("proposal-2")),
      requestContext = requestContext,
      eventDate = eventDate,
      userId = userId
    )

    val sequenceViewed =
      SequenceViewed(id = sequenceId, requestContext = requestContext, eventDate = eventDate)

    val sequenceUpdated =
      SequenceUpdated(
        id = sequenceId,
        requestContext = requestContext,
        eventDate = eventDate,
        userId = userId,
        title = Some("title"),
        status = Some(SequenceStatus.Published),
        operationId = Some(operationId),
        operation = Some("my-operation"),
        themeIds = Seq(ThemeId("theme-1"))
      )

    val sequencePatched =
      SequencePatched(
        id = sequenceId,
        requestContext = requestContext,
        eventDate = eventDate,
        sequence = Sequence(
          sequenceId = sequenceId,
          title = "the sequence",
          slug = "the-sequence",
          proposalIds = Seq(ProposalId("proposal-1"), ProposalId("proposal-2")),
          themeIds = Seq(ThemeId("theme-id")),
          operationId = Some(operationId),
          createdAt = Some(eventDate),
          updatedAt = Some(eventDate),
          status = SequenceStatus.Unpublished,
          creationContext = requestContext,
          sequenceTranslation =
            Seq(SequenceTranslation(slug = "the-sequence", title = "The sequence", language = Language("en"))),
          events = List(
            SequenceAction(
              date = eventDate,
              user = userId,
              actionType = "test",
              arguments = Map("test" -> "test-value")
            )
          ),
          searchable = true
        )
      )

    val sequence = Sequence(
      sequenceId = sequenceId,
      title = "the sequence",
      slug = "the-sequence",
      proposalIds = Seq(ProposalId("proposal-1"), ProposalId("proposal-2")),
      themeIds = Seq(ThemeId("theme-id")),
      operationId = Some(operationId),
      createdAt = Some(eventDate),
      updatedAt = Some(eventDate),
      status = SequenceStatus.Unpublished,
      creationContext = requestContext,
      sequenceTranslation =
        Seq(SequenceTranslation(slug = "the-sequence", title = "The sequence", language = Language("en"))),
      events = List(
        SequenceAction(date = eventDate, user = userId, actionType = "test", arguments = Map("test" -> "test-value"))
      ),
      searchable = true
    )

    persisters.generateTestsFor(
      sample(sequenceCreated),
      sample(sequenceProposalsAdded),
      sample(sequenceProposalsRemoved),
      sample(sequenceViewed),
      sample(sequenceUpdated),
      sample(sequencePatched),
      sample(sequence)
    )
  }
}

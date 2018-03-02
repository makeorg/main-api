package org.make.api.sequence

import java.time.ZonedDateTime

import org.make.api.sequence.PublishedSequenceEvent._
import org.make.core.RequestContext
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
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
        tagIds = Seq(TagId("tag-1"), TagId("tag-2")),
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
        themeIds = Seq(ThemeId("theme-1")),
        tagIds = Seq(TagId("tag-1"), TagId("tag-2"))
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
          tagIds = Seq(TagId("tag-1"), TagId("tag-2")),
          proposalIds = Seq(ProposalId("proposal-1"), ProposalId("proposal-2")),
          themeIds = Seq(ThemeId("theme-id")),
          operationId = Some(operationId),
          createdAt = Some(eventDate),
          updatedAt = Some(eventDate),
          status = SequenceStatus.Unpublished,
          creationContext = requestContext,
          sequenceTranslation = Seq(SequenceTranslation(slug = "the-sequence", title = "The sequence", language = "en")),
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
      tagIds = Seq(TagId("tag-1"), TagId("tag-2")),
      proposalIds = Seq(ProposalId("proposal-1"), ProposalId("proposal-2")),
      themeIds = Seq(ThemeId("theme-id")),
      operationId = Some(operationId),
      createdAt = Some(eventDate),
      updatedAt = Some(eventDate),
      status = SequenceStatus.Unpublished,
      creationContext = requestContext,
      sequenceTranslation = Seq(SequenceTranslation(slug = "the-sequence", title = "The sequence", language = "en")),
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

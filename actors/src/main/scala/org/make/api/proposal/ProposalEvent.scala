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

import com.sksamuel.avro4s.{AvroDefault, AvroSortPriority}
import org.make.api.proposal.ProposalEvent.DeprecatedEvent
import org.make.core.SprayJsonFormatters._
import org.make.core.history.HistoryActions.VoteTrust
import org.make.core.history.HistoryActions.VoteTrust.Trusted
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.make.core._
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.time.ZonedDateTime

sealed trait ProposalEvent extends MakeSerializable {
  def id: ProposalId
  def requestContext: RequestContext
  def eventDate: ZonedDateTime
}

object ProposalEvent {

  trait DeprecatedEvent

  private val defaultDate: ZonedDateTime = ZonedDateTime.parse("2017-11-01T09:00:00Z")

  final case class SimilarProposalsCleared(
    id: ProposalId,
    eventDate: ZonedDateTime = defaultDate,
    requestContext: RequestContext
  ) extends ProposalEvent
      with DeprecatedEvent

  object SimilarProposalsCleared {

    implicit val formatter: RootJsonFormat[SimilarProposalsCleared] =
      DefaultJsonProtocol.jsonFormat3(SimilarProposalsCleared.apply)
  }

  // This event isn't published and so doesn't need to be in the coproduct
  final case class SimilarProposalRemoved(
    id: ProposalId,
    proposalToRemove: ProposalId,
    eventDate: ZonedDateTime = defaultDate,
    requestContext: RequestContext
  ) extends ProposalEvent
      with DeprecatedEvent

  object SimilarProposalRemoved {

    implicit val formatter: RootJsonFormat[SimilarProposalRemoved] =
      DefaultJsonProtocol.jsonFormat4(SimilarProposalRemoved.apply)
  }

}

sealed trait PublishedProposalEvent extends ProposalEvent {
  def version(): Int
  def eventId: Option[EventId]
}

object PublishedProposalEvent {

  private val defaultDate: ZonedDateTime = ZonedDateTime.parse("2017-11-01T09:00:00Z")

  @AvroSortPriority(6)
  final case class ProposalPatched(
    id: ProposalId,
    @AvroDefault("2017-11-01T09:00Z") eventDate: ZonedDateTime = defaultDate,
    requestContext: RequestContext,
    proposal: Proposal,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {
    override def version(): Int = MakeSerializable.V4
  }

  object ProposalPatched {

    implicit val formatter: RootJsonFormat[ProposalPatched] =
      DefaultJsonProtocol.jsonFormat5(ProposalPatched.apply)
  }

  @AvroSortPriority(20)
  final case class ProposalProposed(
    id: ProposalId,
    slug: String,
    requestContext: RequestContext,
    author: ProposalAuthorInfo,
    userId: UserId,
    eventDate: ZonedDateTime,
    content: String,
    operation: Option[OperationId] = None,
    theme: Option[ThemeId] = None,
    language: Option[Language] = None,
    country: Option[Country] = None,
    question: Option[QuestionId] = None,
    initialProposal: Boolean = false,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V3
  }

  object ProposalProposed {

    implicit val formatter: RootJsonFormat[ProposalProposed] =
      DefaultJsonProtocol.jsonFormat14(ProposalProposed.apply)

  }

  final case class ProposalAuthorInfo(
    userId: UserId,
    firstName: Option[String],
    postalCode: Option[String],
    age: Option[Int]
  )

  object ProposalAuthorInfo {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[ProposalAuthorInfo] =
      DefaultJsonProtocol.jsonFormat4(ProposalAuthorInfo.apply)

  }

  @AvroSortPriority(16)
  final case class ProposalViewed(
    id: ProposalId,
    eventDate: ZonedDateTime,
    requestContext: RequestContext,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalViewed {

    implicit val formatter: RootJsonFormat[ProposalViewed] =
      DefaultJsonProtocol.jsonFormat4(ProposalViewed.apply)

  }

  @AvroSortPriority(15)
  final case class ProposalUpdated(
    id: ProposalId,
    eventDate: ZonedDateTime,
    requestContext: RequestContext,
    updatedAt: ZonedDateTime,
    moderator: Option[UserId] = None,
    //@Deprecated(since = "30/10/2017. Use the edition field instead")
    content: String = "",
    edition: Option[ProposalEdition] = None,
    theme: Option[ThemeId] = None,
    labels: Seq[LabelId] = Seq.empty,
    tags: Seq[TagId] = Seq.empty,
    similarProposals: Seq[ProposalId] = Seq.empty,
    idea: Option[IdeaId] = None,
    operation: Option[OperationId] = None,
    question: Option[QuestionId] = None,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalUpdated {

    val actionType: String = "proposal-updated"

    implicit val formatter: RootJsonFormat[ProposalUpdated] =
      DefaultJsonProtocol.jsonFormat15(ProposalUpdated.apply)

  }

  @AvroSortPriority(14)
  final case class ProposalVotesVerifiedUpdated(
    id: ProposalId,
    eventDate: ZonedDateTime,
    requestContext: RequestContext,
    updatedAt: ZonedDateTime,
    moderator: Option[UserId] = None,
    question: Option[QuestionId] = None,
    votesVerified: Seq[Vote],
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalVotesVerifiedUpdated {

    val actionType: String = "proposal-votes-verified-updated"

    implicit val formatter: RootJsonFormat[ProposalVotesVerifiedUpdated] =
      DefaultJsonProtocol.jsonFormat8(ProposalVotesVerifiedUpdated.apply)

  }

  @AvroSortPriority(2)
  final case class ProposalVotesUpdated(
    id: ProposalId,
    eventDate: ZonedDateTime,
    requestContext: RequestContext,
    updatedAt: ZonedDateTime,
    moderator: Option[UserId] = None,
    newVotes: Seq[Vote],
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalVotesUpdated {

    val actionType: String = "proposal-votes-updated"

    implicit val formatter: RootJsonFormat[ProposalVotesUpdated] =
      DefaultJsonProtocol.jsonFormat7(ProposalVotesUpdated.apply)
  }

  @AvroSortPriority(13)
  final case class ReindexProposal(
    id: ProposalId,
    eventDate: ZonedDateTime,
    requestContext: RequestContext,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {
    override def version(): Int = MakeSerializable.V1
  }

  object ReindexProposal {

    val actionType: String = "proposals-tags-updated"

    implicit val formatter: RootJsonFormat[ReindexProposal] =
      DefaultJsonProtocol.jsonFormat4(ReindexProposal.apply)
  }

  @AvroSortPriority(19)
  final case class ProposalAccepted(
    id: ProposalId,
    eventDate: ZonedDateTime,
    requestContext: RequestContext,
    moderator: UserId,
    edition: Option[ProposalEdition],
    sendValidationEmail: Boolean,
    theme: Option[ThemeId],
    labels: Seq[LabelId],
    tags: Seq[TagId],
    similarProposals: Seq[ProposalId],
    idea: Option[IdeaId] = None,
    operation: Option[OperationId] = None,
    question: Option[QuestionId] = None,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalAccepted {

    val actionType: String = "proposal-accepted"

    implicit val formatter: RootJsonFormat[ProposalAccepted] =
      DefaultJsonProtocol.jsonFormat14(ProposalAccepted.apply)

  }

  @AvroSortPriority(18)
  final case class ProposalRefused(
    id: ProposalId,
    eventDate: ZonedDateTime,
    requestContext: RequestContext,
    moderator: UserId,
    sendRefuseEmail: Boolean,
    refusalReason: Option[String],
    operation: Option[OperationId] = None,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalRefused {

    val actionType: String = "proposal-refused"

    implicit val formatter: RootJsonFormat[ProposalRefused] =
      DefaultJsonProtocol.jsonFormat8(ProposalRefused.apply)

  }

  @AvroSortPriority(17)
  final case class ProposalPostponed(
    id: ProposalId,
    @AvroDefault("2017-11-01T09:00Z") eventDate: ZonedDateTime = defaultDate,
    requestContext: RequestContext,
    moderator: UserId,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalPostponed {

    val actionType: String = "proposal-postponed"

    implicit val formatter: RootJsonFormat[ProposalPostponed] =
      DefaultJsonProtocol.jsonFormat5(ProposalPostponed.apply)

  }

  @AvroSortPriority(12)
  final case class ProposalVoted(
    id: ProposalId,
    maybeUserId: Option[UserId],
    eventDate: ZonedDateTime,
    //@Deprecated(since = "05/09/2018. Use the maybeOrganisationId field instead")
    organisationInfo: Option[OrganisationInfo] = None,
    maybeOrganisationId: Option[UserId],
    requestContext: RequestContext,
    voteKey: VoteKey,
    @AvroDefault("trusted") voteTrust: VoteTrust = Trusted,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V4
  }

  object ProposalVoted {

    implicit val formatter: RootJsonFormat[ProposalVoted] =
      DefaultJsonProtocol.jsonFormat9(ProposalVoted.apply)

  }

  @AvroSortPriority(11)
  final case class ProposalUnvoted(
    id: ProposalId,
    maybeUserId: Option[UserId],
    eventDate: ZonedDateTime,
    //@Deprecated(since = "05/09/2018. Use the maybeOrganisationId field instead")
    organisationInfo: Option[OrganisationInfo] = None,
    maybeOrganisationId: Option[UserId],
    requestContext: RequestContext,
    voteKey: VoteKey,
    selectedQualifications: Seq[QualificationKey],
    @AvroDefault("trusted") voteTrust: VoteTrust = Trusted,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V4
  }

  object ProposalUnvoted {

    implicit val formatter: RootJsonFormat[ProposalUnvoted] =
      DefaultJsonProtocol.jsonFormat10(ProposalUnvoted.apply)
  }

  @AvroSortPriority(10)
  final case class ProposalQualified(
    id: ProposalId,
    maybeUserId: Option[UserId],
    eventDate: ZonedDateTime,
    requestContext: RequestContext,
    voteKey: VoteKey,
    qualificationKey: QualificationKey,
    @AvroDefault("trusted") voteTrust: VoteTrust = Trusted,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V2
  }

  object ProposalQualified {

    implicit val formatter: RootJsonFormat[ProposalQualified] =
      DefaultJsonProtocol.jsonFormat8(ProposalQualified.apply)

  }

  @AvroSortPriority(9)
  final case class ProposalUnqualified(
    id: ProposalId,
    maybeUserId: Option[UserId],
    eventDate: ZonedDateTime,
    requestContext: RequestContext,
    voteKey: VoteKey,
    qualificationKey: QualificationKey,
    @AvroDefault("trusted") voteTrust: VoteTrust = Trusted,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V2
  }

  object ProposalUnqualified {

    implicit val formatter: RootJsonFormat[ProposalUnqualified] =
      DefaultJsonProtocol.jsonFormat8(ProposalUnqualified.apply)

  }

  final case class ProposalEdition(oldVersion: String, newVersion: String)

  object ProposalEdition {
    implicit val formatter: RootJsonFormat[ProposalEdition] =
      DefaultJsonProtocol.jsonFormat2(ProposalEdition.apply)

  }

  @AvroSortPriority(8)
  final case class SimilarProposalsAdded(
    id: ProposalId,
    similarProposals: Set[ProposalId],
    requestContext: RequestContext,
    eventDate: ZonedDateTime,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent
      with DeprecatedEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object SimilarProposalsAdded {

    implicit val formatter: RootJsonFormat[SimilarProposalsAdded] =
      DefaultJsonProtocol.jsonFormat5(SimilarProposalsAdded.apply)
  }

  @AvroSortPriority(7)
  final case class ProposalLocked(
    id: ProposalId,
    moderatorId: UserId,
    moderatorName: Option[String] = None,
    @AvroDefault("2017-11-01T09:00Z") eventDate: ZonedDateTime = defaultDate,
    requestContext: RequestContext,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalLocked {

    val actionType: String = "proposal-locked"

    implicit val formatter: RootJsonFormat[ProposalLocked] =
      DefaultJsonProtocol.jsonFormat6(ProposalLocked.apply)

  }

  @AvroSortPriority(5)
  final case class ProposalAddedToOperation(
    id: ProposalId,
    operationId: OperationId,
    moderatorId: UserId,
    @AvroDefault("2017-11-01T09:00Z") eventDate: ZonedDateTime = defaultDate,
    requestContext: RequestContext,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalAddedToOperation {

    implicit val formatter: RootJsonFormat[ProposalAddedToOperation] =
      DefaultJsonProtocol.jsonFormat6(ProposalAddedToOperation.apply)
  }

  @AvroSortPriority(4)
  final case class ProposalRemovedFromOperation(
    id: ProposalId,
    operationId: OperationId,
    moderatorId: UserId,
    @AvroDefault("2017-11-01T09:00Z") eventDate: ZonedDateTime = defaultDate,
    requestContext: RequestContext,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }
  object ProposalRemovedFromOperation {

    implicit val formatter: RootJsonFormat[ProposalRemovedFromOperation] =
      DefaultJsonProtocol.jsonFormat6(ProposalRemovedFromOperation.apply)
  }

  @AvroSortPriority(3)
  final case class ProposalAnonymized(
    id: ProposalId,
    @AvroDefault("2017-11-01T09:00Z") eventDate: ZonedDateTime = defaultDate,
    requestContext: RequestContext,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {
    override def version(): Int = MakeSerializable.V1
  }

  object ProposalAnonymized {

    implicit val formatter: RootJsonFormat[ProposalAnonymized] =
      DefaultJsonProtocol.jsonFormat4(ProposalAnonymized.apply)
  }

  @AvroSortPriority(1)
  final case class ProposalKeywordsSet(
    id: ProposalId,
    eventDate: ZonedDateTime,
    keywords: Seq[ProposalKeyword],
    requestContext: RequestContext,
    eventId: Option[EventId] = None
  ) extends PublishedProposalEvent {
    override def version(): Int = MakeSerializable.V1
  }

  object ProposalKeywordsSet {
    implicit val formatter: RootJsonFormat[ProposalKeywordsSet] =
      DefaultJsonProtocol.jsonFormat5(ProposalKeywordsSet.apply)
  }

}

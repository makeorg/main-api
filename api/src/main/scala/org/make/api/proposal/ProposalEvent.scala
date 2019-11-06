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

import org.make.api.proposal.ProposalEvent.DeprecatedEvent
import org.make.core.SprayJsonFormatters._
import org.make.core.history.HistoryActions.{Trusted, VoteTrust}
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal._
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.tag.TagId
import org.make.core.user.UserId
import org.make.core.{EventWrapper, MakeSerializable, RequestContext}
import shapeless.{:+:, CNil, Coproduct, Poly1}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

sealed trait ProposalEvent extends MakeSerializable {
  def id: ProposalId
  def requestContext: RequestContext
  def eventDate: ZonedDateTime
}

object ProposalEvent {

  trait DeprecatedEvent

  private val defaultDate: ZonedDateTime = ZonedDateTime.parse("2017-11-01T09:00:00Z")

  // This event isn't published and so doesn't need to be in the coproduct
  final case class SimilarProposalsCleared(id: ProposalId,
                                           eventDate: ZonedDateTime = defaultDate,
                                           requestContext: RequestContext = RequestContext.empty)
      extends ProposalEvent
      with DeprecatedEvent

  object SimilarProposalsCleared {

    implicit val formatter: RootJsonFormat[SimilarProposalsCleared] =
      DefaultJsonProtocol.jsonFormat3(SimilarProposalsCleared.apply)
  }

  // This event isn't published and so doesn't need to be in the coproduct
  final case class SimilarProposalRemoved(id: ProposalId,
                                          proposalToRemove: ProposalId,
                                          eventDate: ZonedDateTime = defaultDate,
                                          requestContext: RequestContext = RequestContext.empty)
      extends ProposalEvent
      with DeprecatedEvent

  object SimilarProposalRemoved {

    implicit val formatter: RootJsonFormat[SimilarProposalRemoved] =
      DefaultJsonProtocol.jsonFormat4(SimilarProposalRemoved.apply)
  }

}

sealed trait PublishedProposalEvent extends ProposalEvent {
  def version(): Int
}

object PublishedProposalEvent {

  private val defaultDate: ZonedDateTime = ZonedDateTime.parse("2017-11-01T09:00:00Z")

  type AnyProposalEvent =
    ProposalProposed :+: ProposalAccepted :+: ProposalRefused :+: ProposalPostponed :+: ProposalViewed :+:
      ProposalUpdated :+: ProposalVotesVerifiedUpdated :+: ReindexProposal :+: ProposalVoted :+: ProposalUnvoted :+:
      ProposalQualified :+: ProposalUnqualified :+: SimilarProposalsAdded :+: ProposalLocked :+: ProposalPatched :+:
      ProposalAddedToOperation :+: ProposalRemovedFromOperation :+: ProposalAnonymized :+: ProposalVotesUpdated :+: CNil

  final case class ProposalEventWrapper(version: Int,
                                        id: String,
                                        date: ZonedDateTime,
                                        eventType: String,
                                        event: AnyProposalEvent)
      extends EventWrapper

  object ProposalEventWrapper {
    def wrapEvent(event: PublishedProposalEvent): AnyProposalEvent = event match {
      case e: ProposalProposed             => Coproduct[AnyProposalEvent](e)
      case e: ProposalAccepted             => Coproduct[AnyProposalEvent](e)
      case e: ProposalRefused              => Coproduct[AnyProposalEvent](e)
      case e: ProposalPostponed            => Coproduct[AnyProposalEvent](e)
      case e: ProposalViewed               => Coproduct[AnyProposalEvent](e)
      case e: ProposalUpdated              => Coproduct[AnyProposalEvent](e)
      case e: ProposalVotesVerifiedUpdated => Coproduct[AnyProposalEvent](e)
      case e: ProposalVotesUpdated         => Coproduct[AnyProposalEvent](e)
      case e: ReindexProposal              => Coproduct[AnyProposalEvent](e)
      case e: ProposalVoted                => Coproduct[AnyProposalEvent](e)
      case e: ProposalUnvoted              => Coproduct[AnyProposalEvent](e)
      case e: ProposalQualified            => Coproduct[AnyProposalEvent](e)
      case e: ProposalUnqualified          => Coproduct[AnyProposalEvent](e)
      case e: SimilarProposalsAdded        => Coproduct[AnyProposalEvent](e)
      case e: ProposalLocked               => Coproduct[AnyProposalEvent](e)
      case e: ProposalPatched              => Coproduct[AnyProposalEvent](e)
      case e: ProposalAddedToOperation     => Coproduct[AnyProposalEvent](e)
      case e: ProposalRemovedFromOperation => Coproduct[AnyProposalEvent](e)
      case e: ProposalAnonymized           => Coproduct[AnyProposalEvent](e)
    }
  }

  object ToProposalEvent extends Poly1 {
    implicit val atProposalViewed: Case.Aux[ProposalViewed, ProposalViewed] = at(identity)
    implicit val atProposalUpdated: Case.Aux[ProposalUpdated, ProposalUpdated] = at(identity)
    implicit val atProposalVotesVerifiedUpdated: Case.Aux[ProposalVotesVerifiedUpdated, ProposalVotesVerifiedUpdated] =
      at(identity)
    implicit val atProposalVotesUpdated: Case.Aux[ProposalVotesUpdated, ProposalVotesUpdated] = at(identity)
    implicit val atProposalTagsUpdated: Case.Aux[ReindexProposal, ReindexProposal] = at(identity)
    implicit val atProposalProposed: Case.Aux[ProposalProposed, ProposalProposed] = at(identity)
    implicit val atProposalAccepted: Case.Aux[ProposalAccepted, ProposalAccepted] = at(identity)
    implicit val atProposalRefused: Case.Aux[ProposalRefused, ProposalRefused] = at(identity)
    implicit val atProposalPostponed: Case.Aux[ProposalPostponed, ProposalPostponed] = at(identity)
    implicit val atProposalVoted: Case.Aux[ProposalVoted, ProposalVoted] = at(identity)
    implicit val atProposalUnvoted: Case.Aux[ProposalUnvoted, ProposalUnvoted] = at(identity)
    implicit val atProposalQualified: Case.Aux[ProposalQualified, ProposalQualified] = at(identity)
    implicit val atProposalUnqualified: Case.Aux[ProposalUnqualified, ProposalUnqualified] = at(identity)
    implicit val atSimilarProposalsAdded: Case.Aux[SimilarProposalsAdded, SimilarProposalsAdded] = at(identity)
    implicit val atProposalLocked: Case.Aux[ProposalLocked, ProposalLocked] = at(identity)
    implicit val atProposalPatched: Case.Aux[ProposalPatched, ProposalPatched] = at(identity)
    implicit val atProposalAddedToOperation: Case.Aux[ProposalAddedToOperation, ProposalAddedToOperation] = at(identity)
    implicit val atProposalRemovedFromOperation: Case.Aux[ProposalRemovedFromOperation, ProposalRemovedFromOperation] =
      at(identity)
    implicit val atProposalAnonymized: Case.Aux[ProposalAnonymized, ProposalAnonymized] = at(identity)
  }

  final case class ProposalPatched(id: ProposalId,
                                   eventDate: ZonedDateTime = defaultDate,
                                   requestContext: RequestContext = RequestContext.empty,
                                   proposal: Proposal)
      extends PublishedProposalEvent {
    override def version(): Int = MakeSerializable.V4
  }

  object ProposalPatched {

    implicit val formatter: RootJsonFormat[ProposalPatched] =
      DefaultJsonProtocol.jsonFormat4(ProposalPatched.apply)
  }

  final case class ProposalProposed(id: ProposalId,
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
                                    initialProposal: Boolean = false)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V3
  }

  object ProposalProposed {

    implicit val formatter: RootJsonFormat[ProposalProposed] =
      DefaultJsonProtocol.jsonFormat13(ProposalProposed.apply)

  }

  final case class ProposalAuthorInfo(userId: UserId,
                                      firstName: Option[String],
                                      postalCode: Option[String],
                                      age: Option[Int])

  object ProposalAuthorInfo {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[ProposalAuthorInfo] =
      DefaultJsonProtocol.jsonFormat4(ProposalAuthorInfo.apply)

  }

  final case class ProposalViewed(id: ProposalId, eventDate: ZonedDateTime, requestContext: RequestContext)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalViewed {

    implicit val formatter: RootJsonFormat[ProposalViewed] =
      DefaultJsonProtocol.jsonFormat3(ProposalViewed.apply)

  }

  final case class ProposalUpdated(id: ProposalId,
                                   eventDate: ZonedDateTime,
                                   requestContext: RequestContext,
                                   updatedAt: ZonedDateTime,
                                   moderator: Option[UserId] = None,
                                   @Deprecated content: String = "",
                                   edition: Option[ProposalEdition] = None,
                                   theme: Option[ThemeId] = None,
                                   labels: Seq[LabelId] = Seq.empty,
                                   tags: Seq[TagId] = Seq.empty,
                                   similarProposals: Seq[ProposalId] = Seq.empty,
                                   idea: Option[IdeaId] = None,
                                   operation: Option[OperationId] = None,
                                   question: Option[QuestionId] = None)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalUpdated {
    val actionType: String = "proposal-updated"

    implicit val formatter: RootJsonFormat[ProposalUpdated] =
      DefaultJsonProtocol.jsonFormat14(ProposalUpdated.apply)

  }

  final case class ProposalVotesVerifiedUpdated(id: ProposalId,
                                                eventDate: ZonedDateTime,
                                                requestContext: RequestContext,
                                                updatedAt: ZonedDateTime,
                                                moderator: Option[UserId] = None,
                                                question: Option[QuestionId] = None,
                                                votesVerified: Seq[Vote])
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalVotesVerifiedUpdated {
    val actionType: String = "proposal-votes-verified-updated"

    implicit val formatter: RootJsonFormat[ProposalVotesVerifiedUpdated] =
      DefaultJsonProtocol.jsonFormat7(ProposalVotesVerifiedUpdated.apply)

  }

  final case class ProposalVotesUpdated(id: ProposalId,
                                        eventDate: ZonedDateTime,
                                        requestContext: RequestContext,
                                        updatedAt: ZonedDateTime,
                                        moderator: Option[UserId] = None,
                                        newVotes: Seq[Vote])
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalVotesUpdated {
    val actionType: String = "proposal-votes-updated"

    implicit val formatter: RootJsonFormat[ProposalVotesUpdated] =
      DefaultJsonProtocol.jsonFormat6(ProposalVotesUpdated.apply)
  }

  final case class ReindexProposal(id: ProposalId, eventDate: ZonedDateTime, requestContext: RequestContext)
      extends PublishedProposalEvent {
    override def version(): Int = MakeSerializable.V1
  }

  object ReindexProposal {
    val actionType: String = "proposals-tags-updated"

    implicit val formatter: RootJsonFormat[ReindexProposal] =
      DefaultJsonProtocol.jsonFormat3(ReindexProposal.apply)
  }

  final case class ProposalAccepted(id: ProposalId,
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
                                    question: Option[QuestionId] = None)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalAccepted {
    val actionType: String = "proposal-accepted"

    implicit val formatter: RootJsonFormat[ProposalAccepted] =
      DefaultJsonProtocol.jsonFormat13(ProposalAccepted.apply)

  }

  final case class ProposalRefused(id: ProposalId,
                                   eventDate: ZonedDateTime,
                                   requestContext: RequestContext,
                                   moderator: UserId,
                                   sendRefuseEmail: Boolean,
                                   refusalReason: Option[String],
                                   operation: Option[OperationId] = None)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalRefused {
    val actionType: String = "proposal-refused"

    implicit val formatter: RootJsonFormat[ProposalRefused] =
      DefaultJsonProtocol.jsonFormat7(ProposalRefused.apply)

  }

  final case class ProposalPostponed(id: ProposalId,
                                     eventDate: ZonedDateTime = defaultDate,
                                     requestContext: RequestContext = RequestContext.empty,
                                     moderator: UserId)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalPostponed {
    val actionType: String = "proposal-postponed"

    implicit val formatter: RootJsonFormat[ProposalPostponed] =
      DefaultJsonProtocol.jsonFormat4(ProposalPostponed.apply)

  }

  final case class ProposalVoted(id: ProposalId,
                                 maybeUserId: Option[UserId],
                                 eventDate: ZonedDateTime,
                                 @Deprecated organisationInfo: Option[OrganisationInfo] = None,
                                 maybeOrganisationId: Option[UserId],
                                 requestContext: RequestContext,
                                 voteKey: VoteKey,
                                 voteTrust: VoteTrust = Trusted)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V4
  }

  object ProposalVoted {

    implicit val formatter: RootJsonFormat[ProposalVoted] =
      DefaultJsonProtocol.jsonFormat8(ProposalVoted.apply)

  }

  final case class ProposalUnvoted(id: ProposalId,
                                   maybeUserId: Option[UserId],
                                   eventDate: ZonedDateTime,
                                   @Deprecated organisationInfo: Option[OrganisationInfo] = None,
                                   maybeOrganisationId: Option[UserId],
                                   requestContext: RequestContext,
                                   voteKey: VoteKey,
                                   selectedQualifications: Seq[QualificationKey],
                                   voteTrust: VoteTrust = Trusted)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V4
  }

  object ProposalUnvoted {

    implicit val formatter: RootJsonFormat[ProposalUnvoted] =
      DefaultJsonProtocol.jsonFormat9(ProposalUnvoted.apply)
  }

  final case class ProposalQualified(id: ProposalId,
                                     maybeUserId: Option[UserId],
                                     eventDate: ZonedDateTime,
                                     requestContext: RequestContext,
                                     voteKey: VoteKey,
                                     qualificationKey: QualificationKey,
                                     voteTrust: VoteTrust = Trusted)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V2
  }

  object ProposalQualified {

    implicit val formatter: RootJsonFormat[ProposalQualified] =
      DefaultJsonProtocol.jsonFormat7(ProposalQualified.apply)

  }

  final case class ProposalUnqualified(id: ProposalId,
                                       maybeUserId: Option[UserId],
                                       eventDate: ZonedDateTime,
                                       requestContext: RequestContext,
                                       voteKey: VoteKey,
                                       qualificationKey: QualificationKey,
                                       voteTrust: VoteTrust = Trusted)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V2
  }

  object ProposalUnqualified {

    implicit val formatter: RootJsonFormat[ProposalUnqualified] =
      DefaultJsonProtocol.jsonFormat7(ProposalUnqualified.apply)

  }

  final case class ProposalEdition(oldVersion: String, newVersion: String)

  object ProposalEdition {
    implicit val formatter: RootJsonFormat[ProposalEdition] =
      DefaultJsonProtocol.jsonFormat2(ProposalEdition.apply)

  }

  final case class SimilarProposalsAdded(id: ProposalId,
                                         similarProposals: Set[ProposalId],
                                         requestContext: RequestContext,
                                         eventDate: ZonedDateTime)
      extends PublishedProposalEvent
      with DeprecatedEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object SimilarProposalsAdded {

    implicit val formatter: RootJsonFormat[SimilarProposalsAdded] =
      DefaultJsonProtocol.jsonFormat4(SimilarProposalsAdded.apply)
  }

  final case class ProposalLocked(id: ProposalId,
                                  moderatorId: UserId,
                                  moderatorName: Option[String] = None,
                                  eventDate: ZonedDateTime = defaultDate,
                                  requestContext: RequestContext = RequestContext.empty)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalLocked {
    val actionType: String = "proposal-locked"

    implicit val formatter: RootJsonFormat[ProposalLocked] =
      DefaultJsonProtocol.jsonFormat5(ProposalLocked.apply)

  }

  final case class ProposalAddedToOperation(id: ProposalId,
                                            operationId: OperationId,
                                            moderatorId: UserId,
                                            eventDate: ZonedDateTime = defaultDate,
                                            requestContext: RequestContext = RequestContext.empty)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }

  object ProposalAddedToOperation {

    implicit val formatter: RootJsonFormat[ProposalAddedToOperation] =
      DefaultJsonProtocol.jsonFormat5(ProposalAddedToOperation.apply)
  }

  final case class ProposalRemovedFromOperation(id: ProposalId,
                                                operationId: OperationId,
                                                moderatorId: UserId,
                                                eventDate: ZonedDateTime = defaultDate,
                                                requestContext: RequestContext = RequestContext.empty)
      extends PublishedProposalEvent {

    override def version(): Int = MakeSerializable.V1
  }
  object ProposalRemovedFromOperation {

    implicit val formatter: RootJsonFormat[ProposalRemovedFromOperation] =
      DefaultJsonProtocol.jsonFormat5(ProposalRemovedFromOperation.apply)
  }

  final case class ProposalAnonymized(id: ProposalId,
                                      eventDate: ZonedDateTime = defaultDate,
                                      requestContext: RequestContext = RequestContext.empty)
      extends PublishedProposalEvent {
    override def version(): Int = MakeSerializable.V1
  }

  object ProposalAnonymized {
    implicit val formatter: RootJsonFormat[ProposalAnonymized] =
      DefaultJsonProtocol.jsonFormat3(ProposalAnonymized.apply)
  }

}

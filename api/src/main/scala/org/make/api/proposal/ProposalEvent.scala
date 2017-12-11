package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.core.SprayJsonFormatters._
import org.make.core.proposal.{Proposal, ProposalId, QualificationKey, VoteKey}
import org.make.core.reference.{IdeaId, LabelId, TagId, ThemeId}
import org.make.core.user.UserId
import org.make.core.{EventWrapper, MakeSerializable, RequestContext}
import shapeless.{:+:, CNil, Coproduct}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

sealed trait ProposalEvent extends MakeSerializable {
  def id: ProposalId
  def requestContext: RequestContext
  def eventDate: ZonedDateTime
}

object ProposalEvent {
  // This event isn't published and so doesn't need to be in the coproduct
  final case class SimilarProposalsCleared(id: ProposalId,
                                           eventDate: ZonedDateTime = ZonedDateTime.now(),
                                           requestContext: RequestContext = RequestContext.empty)
      extends ProposalEvent

  object SimilarProposalsCleared {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[SimilarProposalsCleared] =
      DefaultJsonProtocol.jsonFormat3(SimilarProposalsCleared.apply)
  }

  // This event isn't published and so doesn't need to be in the coproduct
  final case class SimilarProposalRemoved(id: ProposalId,
                                          proposalToRemove: ProposalId,
                                          eventDate: ZonedDateTime = ZonedDateTime.now(),
                                          requestContext: RequestContext = RequestContext.empty)
      extends ProposalEvent

  object SimilarProposalRemoved {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[SimilarProposalRemoved] =
      DefaultJsonProtocol.jsonFormat4(SimilarProposalRemoved.apply)
  }

}

sealed trait PublishedProposalEvent extends ProposalEvent

object PublishedProposalEvent {

  type AnyProposalEvent =
    ProposalProposed :+: ProposalAccepted :+: ProposalRefused :+: ProposalPostponed :+: ProposalViewed :+:
      ProposalUpdated :+: ProposalVoted :+: ProposalUnvoted :+: ProposalQualified :+: ProposalUnqualified :+:
      SimilarProposalsAdded :+: ProposalLocked :+: ProposalPatched :+: CNil

  final case class ProposalEventWrapper(version: Int,
                                        id: String,
                                        date: ZonedDateTime,
                                        eventType: String,
                                        event: AnyProposalEvent)
      extends EventWrapper

  object ProposalEventWrapper {
    def wrapEvent(event: PublishedProposalEvent): AnyProposalEvent = event match {
      case e: ProposalProposed      => Coproduct[AnyProposalEvent](e)
      case e: ProposalAccepted      => Coproduct[AnyProposalEvent](e)
      case e: ProposalRefused       => Coproduct[AnyProposalEvent](e)
      case e: ProposalPostponed     => Coproduct[AnyProposalEvent](e)
      case e: ProposalViewed        => Coproduct[AnyProposalEvent](e)
      case e: ProposalUpdated       => Coproduct[AnyProposalEvent](e)
      case e: ProposalVoted         => Coproduct[AnyProposalEvent](e)
      case e: ProposalUnvoted       => Coproduct[AnyProposalEvent](e)
      case e: ProposalQualified     => Coproduct[AnyProposalEvent](e)
      case e: ProposalUnqualified   => Coproduct[AnyProposalEvent](e)
      case e: SimilarProposalsAdded => Coproduct[AnyProposalEvent](e)
      case e: ProposalLocked        => Coproduct[AnyProposalEvent](e)
      case e: ProposalPatched       => Coproduct[AnyProposalEvent](e)
    }
  }

  final case class ProposalPatched(id: ProposalId,
                                   eventDate: ZonedDateTime = ZonedDateTime.now(),
                                   requestContext: RequestContext = RequestContext.empty,
                                   proposal: Proposal)
      extends PublishedProposalEvent
  object ProposalPatched {
    val version: Int = MakeSerializable.V1

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
                                    theme: Option[ThemeId] = None)
      extends PublishedProposalEvent

  object ProposalProposed {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[ProposalProposed] =
      DefaultJsonProtocol.jsonFormat8(ProposalProposed.apply)

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
      extends PublishedProposalEvent

  object ProposalViewed {
    val version: Int = MakeSerializable.V1

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
                                   newIdea: Option[IdeaId] = None)
      extends PublishedProposalEvent

  object ProposalUpdated {
    val version: Int = MakeSerializable.V1
    val actionType: String = "proposal-updated"

    implicit val formatter: RootJsonFormat[ProposalUpdated] =
      DefaultJsonProtocol.jsonFormat12(ProposalUpdated.apply)

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
                                    similarProposals: Seq[ProposalId])
      extends PublishedProposalEvent

  object ProposalAccepted {
    val version: Int = MakeSerializable.V1
    val actionType: String = "proposal-accepted"

    implicit val formatter: RootJsonFormat[ProposalAccepted] =
      DefaultJsonProtocol.jsonFormat10(ProposalAccepted.apply)

  }

  final case class ProposalRefused(id: ProposalId,
                                   eventDate: ZonedDateTime,
                                   requestContext: RequestContext,
                                   moderator: UserId,
                                   sendRefuseEmail: Boolean,
                                   refusalReason: Option[String])
      extends PublishedProposalEvent

  object ProposalRefused {
    val version: Int = MakeSerializable.V1
    val actionType: String = "proposal-refused"

    implicit val formatter: RootJsonFormat[ProposalRefused] =
      DefaultJsonProtocol.jsonFormat6(ProposalRefused.apply)

  }

  final case class ProposalPostponed(id: ProposalId,
                                     eventDate: ZonedDateTime = ZonedDateTime.now(),
                                     requestContext: RequestContext = RequestContext.empty,
                                     moderator: UserId)
      extends PublishedProposalEvent

  object ProposalPostponed {
    val version: Int = MakeSerializable.V1
    val actionType: String = "proposal-postponed"

    implicit val formatter: RootJsonFormat[ProposalPostponed] =
      DefaultJsonProtocol.jsonFormat4(ProposalPostponed.apply)

  }

  final case class ProposalVoted(id: ProposalId,
                                 maybeUserId: Option[UserId],
                                 eventDate: ZonedDateTime,
                                 requestContext: RequestContext,
                                 voteKey: VoteKey)
      extends PublishedProposalEvent

  object ProposalVoted {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[ProposalVoted] =
      DefaultJsonProtocol.jsonFormat5(ProposalVoted.apply)

  }

  final case class ProposalUnvoted(id: ProposalId,
                                   maybeUserId: Option[UserId],
                                   eventDate: ZonedDateTime,
                                   requestContext: RequestContext,
                                   voteKey: VoteKey,
                                   selectedQualifications: Seq[QualificationKey])
      extends PublishedProposalEvent

  object ProposalUnvoted {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[ProposalUnvoted] =
      DefaultJsonProtocol.jsonFormat6(ProposalUnvoted.apply)
  }

  final case class ProposalQualified(id: ProposalId,
                                     maybeUserId: Option[UserId],
                                     eventDate: ZonedDateTime,
                                     requestContext: RequestContext,
                                     voteKey: VoteKey,
                                     qualificationKey: QualificationKey)
      extends PublishedProposalEvent

  object ProposalQualified {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[ProposalQualified] =
      DefaultJsonProtocol.jsonFormat6(ProposalQualified.apply)

  }

  final case class ProposalUnqualified(id: ProposalId,
                                       maybeUserId: Option[UserId],
                                       eventDate: ZonedDateTime,
                                       requestContext: RequestContext,
                                       voteKey: VoteKey,
                                       qualificationKey: QualificationKey)
      extends PublishedProposalEvent

  object ProposalUnqualified {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[ProposalUnqualified] =
      DefaultJsonProtocol.jsonFormat6(ProposalUnqualified.apply)

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

  object SimilarProposalsAdded {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[SimilarProposalsAdded] =
      DefaultJsonProtocol.jsonFormat4(SimilarProposalsAdded.apply)
  }

  final case class ProposalLocked(id: ProposalId,
                                  moderatorId: UserId,
                                  moderatorName: Option[String] = None,
                                  eventDate: ZonedDateTime = ZonedDateTime.now(),
                                  requestContext: RequestContext = RequestContext.empty)
      extends PublishedProposalEvent

  object ProposalLocked {
    val version: Int = MakeSerializable.V1
    val actionType: String = "proposal-locked"

    implicit val formatter: RootJsonFormat[ProposalLocked] =
      DefaultJsonProtocol.jsonFormat5(ProposalLocked.apply)

  }

}

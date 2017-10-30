package org.make.api.proposal

import java.time.ZonedDateTime

import org.make.core.SprayJsonFormatters._
import org.make.core.proposal.{ProposalId, QualificationKey, VoteKey}
import org.make.core.reference.{LabelId, TagId, ThemeId}
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

  type AnyProposalEvent = ProposalProposed :+: ProposalAccepted :+: ProposalRefused :+: ProposalViewed :+:
    ProposalUpdated :+: ProposalVoted :+: ProposalUnvoted :+: ProposalQualified :+: ProposalUnqualified :+: CNil

  final case class ProposalEventWrapper(version: Int,
                                        id: String,
                                        date: ZonedDateTime,
                                        eventType: String,
                                        event: AnyProposalEvent)
      extends EventWrapper

  object ProposalEventWrapper {
    def wrapEvent(event: ProposalEvent): AnyProposalEvent = event match {
      case e: ProposalProposed    => Coproduct[AnyProposalEvent](e)
      case e: ProposalAccepted    => Coproduct[AnyProposalEvent](e)
      case e: ProposalRefused     => Coproduct[AnyProposalEvent](e)
      case e: ProposalViewed      => Coproduct[AnyProposalEvent](e)
      case e: ProposalUpdated     => Coproduct[AnyProposalEvent](e)
      case e: ProposalVoted       => Coproduct[AnyProposalEvent](e)
      case e: ProposalUnvoted     => Coproduct[AnyProposalEvent](e)
      case e: ProposalQualified   => Coproduct[AnyProposalEvent](e)
      case e: ProposalUnqualified => Coproduct[AnyProposalEvent](e)
    }
  }

  final case class ProposalProposed(id: ProposalId,
                                    slug: String,
                                    requestContext: RequestContext,
                                    author: ProposalAuthorInfo,
                                    userId: UserId,
                                    eventDate: ZonedDateTime,
                                    content: String,
                                    theme: Option[ThemeId] = None)
      extends ProposalEvent

  object ProposalProposed {
    val version: Int = MakeSerializable.V1

    implicit val proposalProposedFormatter: RootJsonFormat[ProposalProposed] =
      DefaultJsonProtocol.jsonFormat8(ProposalProposed.apply)

  }

  final case class ProposalAuthorInfo(userId: UserId,
                                      firstName: Option[String],
                                      postalCode: Option[String],
                                      age: Option[Int])

  object ProposalAuthorInfo {
    val version: Int = MakeSerializable.V1

    implicit val proposalAuthorInfoFormatter: RootJsonFormat[ProposalAuthorInfo] =
      DefaultJsonProtocol.jsonFormat4(ProposalAuthorInfo.apply)

  }

  final case class ProposalViewed(id: ProposalId, eventDate: ZonedDateTime, requestContext: RequestContext)
      extends ProposalEvent

  object ProposalViewed {
    val version: Int = MakeSerializable.V1

    implicit val proposalViewedFormatter: RootJsonFormat[ProposalViewed] =
      DefaultJsonProtocol.jsonFormat3(ProposalViewed.apply)

  }

  final case class ProposalUpdated(id: ProposalId,
                                   eventDate: ZonedDateTime,
                                   requestContext: RequestContext,
                                   updatedAt: ZonedDateTime,
                                   moderator: UserId,
                                   @Deprecated content: String = "",
                                   edition: Option[ProposalEdition] = None,
                                   theme: Option[ThemeId] = None,
                                   labels: Seq[LabelId] = Seq.empty,
                                   tags: Seq[TagId] = Seq.empty,
                                   similarProposals: Seq[ProposalId] = Seq.empty)
      extends ProposalEvent

  object ProposalUpdated {
    val version: Int = MakeSerializable.V1
    val actionType: String = "proposal-updated"

    implicit val proposalUpdatedFormatter: RootJsonFormat[ProposalUpdated] =
      DefaultJsonProtocol.jsonFormat11(ProposalUpdated.apply)

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
      extends ProposalEvent

  object ProposalAccepted {
    val version: Int = MakeSerializable.V1
    val actionType: String = "proposal-accepted"

    implicit val proposalAcceptedFormatter: RootJsonFormat[ProposalAccepted] =
      DefaultJsonProtocol.jsonFormat10(ProposalAccepted.apply)

  }

  final case class ProposalRefused(id: ProposalId,
                                   eventDate: ZonedDateTime,
                                   requestContext: RequestContext,
                                   moderator: UserId,
                                   sendRefuseEmail: Boolean,
                                   refusalReason: Option[String])
      extends ProposalEvent

  object ProposalRefused {
    val version: Int = MakeSerializable.V1
    val actionType: String = "proposal-refused"

    implicit val proposalRefusedFormatter: RootJsonFormat[ProposalRefused] =
      DefaultJsonProtocol.jsonFormat6(ProposalRefused.apply)

  }

  final case class ProposalVoted(id: ProposalId,
                                 maybeUserId: Option[UserId],
                                 eventDate: ZonedDateTime,
                                 requestContext: RequestContext,
                                 voteKey: VoteKey)
      extends ProposalEvent

  object ProposalVoted {
    val version: Int = MakeSerializable.V1

    implicit val proposalVotedFormatter: RootJsonFormat[ProposalVoted] =
      DefaultJsonProtocol.jsonFormat5(ProposalVoted.apply)

  }

  final case class ProposalUnvoted(id: ProposalId,
                                   maybeUserId: Option[UserId],
                                   eventDate: ZonedDateTime,
                                   requestContext: RequestContext,
                                   voteKey: VoteKey,
                                   selectedQualifications: Seq[QualificationKey])
      extends ProposalEvent

  object ProposalUnvoted {
    val version: Int = MakeSerializable.V1

    implicit val proposalUnvotedFormatter: RootJsonFormat[ProposalUnvoted] =
      DefaultJsonProtocol.jsonFormat6(ProposalUnvoted.apply)
  }

  final case class ProposalQualified(id: ProposalId,
                                     maybeUserId: Option[UserId],
                                     eventDate: ZonedDateTime,
                                     requestContext: RequestContext,
                                     voteKey: VoteKey,
                                     qualificationKey: QualificationKey)
      extends ProposalEvent

  object ProposalQualified {
    val version: Int = MakeSerializable.V1

    implicit val proposalQualifiedFormatter: RootJsonFormat[ProposalQualified] =
      DefaultJsonProtocol.jsonFormat6(ProposalQualified.apply)

  }

  final case class ProposalUnqualified(id: ProposalId,
                                       maybeUserId: Option[UserId],
                                       eventDate: ZonedDateTime,
                                       requestContext: RequestContext,
                                       voteKey: VoteKey,
                                       qualificationKey: QualificationKey)
      extends ProposalEvent

  object ProposalUnqualified {
    val version: Int = MakeSerializable.V1

    implicit val proposalUnqualifiedFormatter: RootJsonFormat[ProposalUnqualified] =
      DefaultJsonProtocol.jsonFormat6(ProposalUnqualified.apply)

  }

  final case class ProposalEdition(oldVersion: String, newVersion: String)

  object ProposalEdition {
    implicit val proposalEditionFormatter: RootJsonFormat[ProposalEdition] =
      DefaultJsonProtocol.jsonFormat2(ProposalEdition.apply)

  }

}

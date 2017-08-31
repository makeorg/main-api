package org.make.core.proposal

import java.time.ZonedDateTime

import org.make.core.reference.{LabelId, TagId, ThemeId}
import org.make.core.{EventWrapper, MakeSerializable, RequestContext}
import org.make.core.user.UserId
import shapeless.{:+:, CNil, Coproduct}

sealed trait ProposalEvent extends MakeSerializable {
  def id: ProposalId
  def context: RequestContext
  def eventDate: ZonedDateTime
}

object ProposalEvent {

  type AnyProposalEvent =
    ProposalProposed :+: ProposalAccepted :+: ProposalViewed :+: ProposalUpdated :+: CNil

  final case class ProposalEventWrapper(version: Int,
                                        id: String,
                                        date: ZonedDateTime,
                                        eventType: String,
                                        event: AnyProposalEvent)
      extends EventWrapper

  object ProposalEventWrapper {
    def wrapEvent(event: ProposalEvent): AnyProposalEvent = event match {
      case e: ProposalProposed => Coproduct[AnyProposalEvent](e)
      case e: ProposalAccepted => Coproduct[AnyProposalEvent](e)
      case e: ProposalViewed   => Coproduct[AnyProposalEvent](e)
      case e: ProposalUpdated  => Coproduct[AnyProposalEvent](e)
    }
  }

  final case class ProposalProposed(id: ProposalId,
                                    slug: String,
                                    context: RequestContext,
                                    author: ProposalAuthorInfo,
                                    userId: UserId,
                                    eventDate: ZonedDateTime,
                                    content: String)
      extends ProposalEvent

  object ProposalProposed {
    val version: Int = MakeSerializable.V1
  }

  final case class ProposalAuthorInfo(userId: UserId,
                                      firstName: Option[String],
                                      postalCode: Option[String],
                                      age: Option[Int])

  object ProposalAuthorInfo {
    val version: Int = MakeSerializable.V1
  }

  final case class ProposalViewed(id: ProposalId, eventDate: ZonedDateTime, context: RequestContext)
      extends ProposalEvent

  object ProposalViewed {
    val version: Int = MakeSerializable.V1
  }

  final case class ProposalUpdated(id: ProposalId,
                                   eventDate: ZonedDateTime,
                                   context: RequestContext,
                                   updatedAt: ZonedDateTime,
                                   content: String)
      extends ProposalEvent

  object ProposalUpdated {
    val version: Int = MakeSerializable.V1
  }

  final case class ProposalAccepted(id: ProposalId,
                                    eventDate: ZonedDateTime,
                                    context: RequestContext,
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
  }

  final case class ProposalEdition(oldVersion: String, newVersion: String)
}

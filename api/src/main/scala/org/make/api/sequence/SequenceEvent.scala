package org.make.api.sequence

import java.time.ZonedDateTime

import org.make.core.SprayJsonFormatters._
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence.{SequenceId, SequenceStatus}
import org.make.core.user.UserId
import org.make.core.{EventWrapper, MakeSerializable, RequestContext}
import shapeless.{:+:, CNil, Coproduct}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

sealed trait SequenceEvent extends MakeSerializable {
  def id: SequenceId
  def requestContext: RequestContext
  def eventDate: ZonedDateTime
}

object SequenceEvent {

  type AnySequenceEvent =
    SequenceCreated :+: SequenceViewed :+: SequenceUpdated :+:
      SequenceProposalsRemoved :+: SequenceProposalsAdded :+: CNil

  final case class SequenceEventWrapper(version: Int,
                                        id: String,
                                        date: ZonedDateTime,
                                        eventType: String,
                                        event: AnySequenceEvent)
      extends EventWrapper

  object SequenceEventWrapper {
    def wrapEvent(event: SequenceEvent): AnySequenceEvent = event match {
      case e: SequenceCreated          => Coproduct[AnySequenceEvent](e)
      case e: SequenceViewed           => Coproduct[AnySequenceEvent](e)
      case e: SequenceUpdated          => Coproduct[AnySequenceEvent](e)
      case e: SequenceProposalsAdded   => Coproduct[AnySequenceEvent](e)
      case e: SequenceProposalsRemoved => Coproduct[AnySequenceEvent](e)
    }
  }

  final case class SequenceProposalsAdded(id: SequenceId,
                                          proposalIds: Seq[ProposalId],
                                          requestContext: RequestContext,
                                          eventDate: ZonedDateTime,
                                          userId: UserId)
      extends SequenceEvent
  object SequenceProposalsAdded {
    val version: Int = MakeSerializable.V1
    val actionType: String = "sequence-proposal-added"

    implicit val sequenceProposalsAddedFormatter: RootJsonFormat[SequenceProposalsAdded] =
      DefaultJsonProtocol.jsonFormat5(SequenceProposalsAdded.apply)
  }

  final case class SequenceProposalsRemoved(id: SequenceId,
                                            proposalIds: Seq[ProposalId],
                                            requestContext: RequestContext,
                                            eventDate: ZonedDateTime,
                                            userId: UserId)
      extends SequenceEvent
  object SequenceProposalsRemoved {
    val version: Int = MakeSerializable.V1
    val actionType: String = "sequence-proposal-added"

    implicit val sequenceProposalsRemovedFormatter: RootJsonFormat[SequenceProposalsRemoved] =
      DefaultJsonProtocol.jsonFormat5(SequenceProposalsRemoved.apply)

  }

  final case class SequenceCreated(id: SequenceId,
                                   slug: String,
                                   requestContext: RequestContext,
                                   userId: UserId,
                                   eventDate: ZonedDateTime,
                                   title: String,
                                   themeIds: Seq[ThemeId],
                                   tagIds: Seq[TagId],
                                   searchable: Boolean)
      extends SequenceEvent

  object SequenceCreated {
    val version: Int = MakeSerializable.V1
    val actionType: String = "sequence-created"

    implicit val sequenceCreatedFormatter: RootJsonFormat[SequenceCreated] =
      DefaultJsonProtocol.jsonFormat9(SequenceCreated.apply)
  }

  final case class SequenceViewed(id: SequenceId, eventDate: ZonedDateTime, requestContext: RequestContext)
      extends SequenceEvent

  object SequenceViewed {
    val version: Int = MakeSerializable.V1

    implicit val sequenceViewed: RootJsonFormat[SequenceViewed] =
      DefaultJsonProtocol.jsonFormat3(SequenceViewed.apply)
  }

  final case class SequenceUpdated(id: SequenceId,
                                   userId: UserId,
                                   eventDate: ZonedDateTime,
                                   requestContext: RequestContext,
                                   title: Option[String],
                                   status: Option[SequenceStatus],
                                   operation: Option[String] = None,
                                   themeIds: Seq[ThemeId],
                                   tagIds: Seq[TagId])
      extends SequenceEvent

  object SequenceUpdated {
    val version: Int = MakeSerializable.V1
    val actionType: String = "sequence-updated"

    implicit val sequenceUpdated: RootJsonFormat[SequenceUpdated] =
      DefaultJsonProtocol.jsonFormat9(SequenceUpdated.apply)
  }

  final case class SequenceEdition(oldVersion: String, newVersion: String)
  object SequenceEdition {
    implicit val sequenceEditionFormatter: RootJsonFormat[SequenceEdition] =
      DefaultJsonProtocol.jsonFormat2(SequenceEdition.apply)
  }
}

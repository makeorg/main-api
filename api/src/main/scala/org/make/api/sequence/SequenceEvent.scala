package org.make.api.sequence

import java.time.ZonedDateTime

import org.make.core.SprayJsonFormatters._
import org.make.core.operation.OperationId
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.sequence.{Sequence, SequenceId, SequenceStatus}
import org.make.core.user.UserId
import org.make.core.{EventWrapper, MakeSerializable, RequestContext}
import shapeless.{:+:, CNil, Coproduct, Poly1}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

sealed trait SequenceEvent extends MakeSerializable {
  def id: SequenceId
  def requestContext: RequestContext
  def eventDate: ZonedDateTime
}

object SequenceEvent {}

sealed trait PublishedSequenceEvent extends SequenceEvent

object PublishedSequenceEvent {

  type AnySequenceEvent =
    SequenceCreated :+: SequenceViewed :+: SequenceUpdated :+:
      SequenceProposalsRemoved :+: SequenceProposalsAdded :+: SequencePatched :+: CNil

  final case class SequenceEventWrapper(version: Int,
                                        id: String,
                                        date: ZonedDateTime,
                                        eventType: String,
                                        event: AnySequenceEvent)
      extends EventWrapper

  object SequenceEventWrapper {
    def wrapEvent(event: PublishedSequenceEvent): AnySequenceEvent = event match {
      case e: SequenceCreated          => Coproduct[AnySequenceEvent](e)
      case e: SequenceViewed           => Coproduct[AnySequenceEvent](e)
      case e: SequenceUpdated          => Coproduct[AnySequenceEvent](e)
      case e: SequenceProposalsAdded   => Coproduct[AnySequenceEvent](e)
      case e: SequenceProposalsRemoved => Coproduct[AnySequenceEvent](e)
      case e: SequencePatched          => Coproduct[AnySequenceEvent](e)
    }
  }

  object ToSequenceEvent extends Poly1 {
    implicit val atSequenceViewed: Case.Aux[SequenceViewed, SequenceViewed] = at(identity)
    implicit val atSequenceUpdated: Case.Aux[SequenceUpdated, SequenceUpdated] = at(identity)
    implicit val atSequenceCreated: Case.Aux[SequenceCreated, SequenceCreated] = at(identity)
    implicit val atSequenceProposalsAdded: Case.Aux[SequenceProposalsAdded, SequenceProposalsAdded] = at(identity)
    implicit val atSequenceProposalsRemoved: Case.Aux[SequenceProposalsRemoved, SequenceProposalsRemoved] = at(identity)
    implicit val atSequencePatched: Case.Aux[SequencePatched, SequencePatched] = at(identity)
  }

  final case class SequencePatched(id: SequenceId,
                                   eventDate: ZonedDateTime = ZonedDateTime.now(),
                                   requestContext: RequestContext = RequestContext.empty,
                                   sequence: Sequence)
      extends PublishedSequenceEvent
  object SequencePatched {
    val version: Int = MakeSerializable.V1

    implicit val formatter: RootJsonFormat[SequencePatched] =
      DefaultJsonProtocol.jsonFormat4(SequencePatched.apply)
  }

  final case class SequenceProposalsAdded(id: SequenceId,
                                          proposalIds: Seq[ProposalId],
                                          requestContext: RequestContext,
                                          eventDate: ZonedDateTime,
                                          userId: UserId)
      extends PublishedSequenceEvent
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
      extends PublishedSequenceEvent
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
                                   operationId: Option[OperationId] = None,
                                   searchable: Boolean)
      extends PublishedSequenceEvent

  object SequenceCreated {
    val version: Int = MakeSerializable.V1
    val actionType: String = "sequence-created"

    implicit val sequenceCreatedFormatter: RootJsonFormat[SequenceCreated] =
      DefaultJsonProtocol.jsonFormat10(SequenceCreated.apply)
  }

  final case class SequenceViewed(id: SequenceId, eventDate: ZonedDateTime, requestContext: RequestContext)
      extends PublishedSequenceEvent

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
                                   @Deprecated operation: Option[String] = None,
                                   operationId: Option[OperationId] = None,
                                   themeIds: Seq[ThemeId],
                                   tagIds: Seq[TagId])
      extends PublishedSequenceEvent

  object SequenceUpdated {
    val version: Int = MakeSerializable.V1
    val actionType: String = "sequence-updated"

    implicit val sequenceUpdated: RootJsonFormat[SequenceUpdated] =
      DefaultJsonProtocol.jsonFormat10(SequenceUpdated.apply)
  }

  final case class SequenceEdition(oldVersion: String, newVersion: String)
  object SequenceEdition {
    implicit val sequenceEditionFormatter: RootJsonFormat[SequenceEdition] =
      DefaultJsonProtocol.jsonFormat2(SequenceEdition.apply)
  }
}

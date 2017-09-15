package org.make.api.proposal

import org.make.api.technical.SprayJsonFormatters
import org.make.core.proposal.ProposalEvent._
import org.make.core.proposal.{Proposal, ProposalAction}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import stamina.V1
import stamina.json._

object ProposalSerializers extends SprayJsonFormatters {

  implicit val proposalAuthorInfoFormatter: RootJsonFormat[ProposalAuthorInfo] =
    DefaultJsonProtocol.jsonFormat4(ProposalAuthorInfo.apply)

  implicit val proposalProposedFormatter: RootJsonFormat[ProposalProposed] =
    DefaultJsonProtocol.jsonFormat7(ProposalProposed.apply)

  implicit val proposalViewedFormatter: RootJsonFormat[ProposalViewed] =
    DefaultJsonProtocol.jsonFormat3(ProposalViewed.apply)

  implicit val proposalUpdatedFormatter: RootJsonFormat[ProposalUpdated] =
    DefaultJsonProtocol.jsonFormat5(ProposalUpdated.apply)

  implicit val proposalEditionFormatter: RootJsonFormat[ProposalEdition] =
    DefaultJsonProtocol.jsonFormat2(ProposalEdition.apply)

  implicit val proposalAcceptedFormatter: RootJsonFormat[ProposalAccepted] =
    DefaultJsonProtocol.jsonFormat10(ProposalAccepted.apply)

  implicit val proposalRefuseedFormatter: RootJsonFormat[ProposalRefused] =
    DefaultJsonProtocol.jsonFormat10(ProposalRefused.apply)

  implicit val proposalActionFormatter: RootJsonFormat[ProposalAction] =
    DefaultJsonProtocol.jsonFormat4(ProposalAction.apply)

  implicit val proposalFormatter: RootJsonFormat[Proposal] =
    DefaultJsonProtocol.jsonFormat13(Proposal.apply)

  private val proposalProposedSerializer: JsonPersister[ProposalProposed, V1] =
    persister[ProposalProposed]("proposal-proposed")

  private val proposalViewedSerializer: JsonPersister[ProposalViewed, V1] =
    persister[ProposalViewed]("proposal-viewed")

  private val proposalUpdatedSerializer: JsonPersister[ProposalUpdated, V1] =
    persister[ProposalUpdated]("proposal-updated")

  private val proposalAcceptedSerializer: JsonPersister[ProposalAccepted, V1] =
    persister[ProposalAccepted]("proposal-accepted")

  private val proposalRefusedSerializer: JsonPersister[ProposalRefused, V1] =
    persister[ProposalRefused]("proposal-refused")

  private val proposalSerializer: JsonPersister[Proposal, V1] =
    persister[Proposal]("proposal")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      proposalProposedSerializer,
      proposalViewedSerializer,
      proposalUpdatedSerializer,
      proposalAcceptedSerializer,
      proposalRefusedSerializer,
      proposalSerializer
    )
}

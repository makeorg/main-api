package org.make.api.proposal

import org.make.api.technical.SprayJsonFormatters
import org.make.core.RequestContext
import org.make.core.proposal.{Proposal, ProposalAction}
import org.make.core.proposal.ProposalEvent._
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import stamina.V1
import stamina.json._

object ProposalSerializers extends SprayJsonFormatters {

  implicit private val requestContextFormatter: RootJsonFormat[RequestContext] =
    DefaultJsonProtocol.jsonFormat10(RequestContext.apply)

  implicit private val proposalAuthorInfoFormatter: RootJsonFormat[ProposalAuthorInfo] =
    DefaultJsonProtocol.jsonFormat4(ProposalAuthorInfo.apply)

  implicit private val proposalProposedFormatter: RootJsonFormat[ProposalProposed] =
    DefaultJsonProtocol.jsonFormat7(ProposalProposed.apply)

  implicit private val proposalViewedFormatter: RootJsonFormat[ProposalViewed] =
    DefaultJsonProtocol.jsonFormat3(ProposalViewed.apply)

  implicit private val proposalUpdatedFormatter: RootJsonFormat[ProposalUpdated] =
    DefaultJsonProtocol.jsonFormat5(ProposalUpdated.apply)

  implicit private val proposalEditionFormatter: RootJsonFormat[ProposalEdition] =
    DefaultJsonProtocol.jsonFormat2(ProposalEdition.apply)

  implicit private val proposalAcceptedFormatter: RootJsonFormat[ProposalAccepted] =
    DefaultJsonProtocol.jsonFormat10(ProposalAccepted.apply)

  implicit private val proposalActionFormatter: RootJsonFormat[ProposalAction] =
    DefaultJsonProtocol.jsonFormat4(ProposalAction.apply)

  implicit private val proposalFormatter: RootJsonFormat[Proposal] =
    DefaultJsonProtocol.jsonFormat12(Proposal.apply)

  private val proposalProposedSerializer: JsonPersister[ProposalProposed, V1] =
    persister[ProposalProposed]("proposal-proposed")

  private val proposalViewedSerializer: JsonPersister[ProposalViewed, V1] =
    persister[ProposalViewed]("proposal-viewed")

  private val proposalUpdatedSerializer: JsonPersister[ProposalUpdated, V1] =
    persister[ProposalUpdated]("proposal-updated")

  private val proposalAcceptedSerializer: JsonPersister[ProposalAccepted, V1] =
    persister[ProposalAccepted]("proposal-accepted")

  private val proposalSerializer: JsonPersister[Proposal, V1] =
    persister[Proposal]("proposal")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      proposalProposedSerializer,
      proposalViewedSerializer,
      proposalUpdatedSerializer,
      proposalAcceptedSerializer,
      proposalSerializer
    )
}

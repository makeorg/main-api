package org.make.api.proposal

import org.make.api.technical.SprayJsonFormatters
import org.make.core.RequestContext
import org.make.core.proposal.Proposal
import org.make.core.proposal.ProposalEvent.{ProposalAuthorInfo, ProposalProposed, ProposalUpdated, ProposalViewed}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import stamina.V1
import stamina.json._

object ProposalSerializers extends SprayJsonFormatters {

  implicit private val requestContextFormatter: RootJsonFormat[RequestContext] =
    DefaultJsonProtocol.jsonFormat5(RequestContext.apply)

  implicit private val proposalAuthorInfoFormatter: RootJsonFormat[ProposalAuthorInfo] =
    DefaultJsonProtocol.jsonFormat4(ProposalAuthorInfo.apply)

  implicit private val proposalProposedFormatter: RootJsonFormat[ProposalProposed] =
    DefaultJsonProtocol.jsonFormat7(ProposalProposed.apply)

  implicit private val proposalViewedFormatter: RootJsonFormat[ProposalViewed] =
    DefaultJsonProtocol.jsonFormat2(ProposalViewed.apply)

  implicit private val proposalUpdatedFormatter: RootJsonFormat[ProposalUpdated] =
    DefaultJsonProtocol.jsonFormat4(ProposalUpdated.apply)

  implicit private val proposalFormatter: RootJsonFormat[Proposal] =
    DefaultJsonProtocol.jsonFormat10(Proposal.apply)

  private val proposalProposedSerializer: JsonPersister[ProposalProposed, V1] =
    persister[ProposalProposed]("proposal-proposed")

  private val proposalViewedSerializer: JsonPersister[ProposalViewed, V1] =
    persister[ProposalViewed]("proposal-viewed")

  private val proposalUpdatedSerializer: JsonPersister[ProposalUpdated, V1] =
    persister[ProposalUpdated]("proposal-updated")

  private val proposalSerializer: JsonPersister[Proposal, V1] =
    persister[Proposal]("proposal")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(proposalProposedSerializer, proposalViewedSerializer, proposalUpdatedSerializer, proposalSerializer)
}

package org.make.api.proposal

import org.make.api.technical.SprayJsonFormatters
import org.make.core.proposal.Proposal
import org.make.core.proposal.ProposalEvent.{ProposalProposed, ProposalUpdated, ProposalViewed}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import stamina.V1
import stamina.json._

object ProposalSerializers extends SprayJsonFormatters {

  implicit private val proposalProposedFormatter: RootJsonFormat[ProposalProposed] =
    DefaultJsonProtocol.jsonFormat4(ProposalProposed)

  implicit private val proposalViewedFormatter: RootJsonFormat[ProposalViewed] =
    DefaultJsonProtocol.jsonFormat1(ProposalViewed)

  implicit private val proposalUpdatedFormatter: RootJsonFormat[ProposalUpdated] =
    DefaultJsonProtocol.jsonFormat3(ProposalUpdated)

  implicit private val proposalFormatter: RootJsonFormat[Proposal] =
    DefaultJsonProtocol.jsonFormat5(Proposal)

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

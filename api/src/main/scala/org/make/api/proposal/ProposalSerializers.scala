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
    DefaultJsonProtocol.jsonFormat8(ProposalProposed.apply)

  implicit val proposalViewedFormatter: RootJsonFormat[ProposalViewed] =
    DefaultJsonProtocol.jsonFormat3(ProposalViewed.apply)

  implicit val proposalUpdatedFormatter: RootJsonFormat[ProposalUpdated] =
    DefaultJsonProtocol.jsonFormat5(ProposalUpdated.apply)

  implicit val proposalEditionFormatter: RootJsonFormat[ProposalEdition] =
    DefaultJsonProtocol.jsonFormat2(ProposalEdition.apply)

  implicit val proposalAcceptedFormatter: RootJsonFormat[ProposalAccepted] =
    DefaultJsonProtocol.jsonFormat10(ProposalAccepted.apply)

  implicit val proposalRefuseedFormatter: RootJsonFormat[ProposalRefused] =
    DefaultJsonProtocol.jsonFormat6(ProposalRefused.apply)

  implicit val proposalVotedFormatter: RootJsonFormat[ProposalVoted] =
    DefaultJsonProtocol.jsonFormat5(ProposalVoted.apply)

  implicit val proposalUnvotedFormatter: RootJsonFormat[ProposalUnvoted] =
    DefaultJsonProtocol.jsonFormat5(ProposalUnvoted.apply)

  implicit val proposalQualifiedFormatter: RootJsonFormat[ProposalQualified] =
    DefaultJsonProtocol.jsonFormat6(ProposalQualified.apply)

  implicit val proposalUnqualifiedFormatter: RootJsonFormat[ProposalUnqualified] =
    DefaultJsonProtocol.jsonFormat6(ProposalUnqualified.apply)

  implicit val proposalActionFormatter: RootJsonFormat[ProposalAction] =
    DefaultJsonProtocol.jsonFormat4(ProposalAction.apply)

  implicit val proposalFormatter: RootJsonFormat[Proposal] =
    DefaultJsonProtocol.jsonFormat14(Proposal.apply)

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

  private val proposalVotedSerializer: JsonPersister[ProposalVoted, V1] =
    persister[ProposalVoted]("proposal-voted")

  private val proposalUnvotedSerializer: JsonPersister[ProposalUnvoted, V1] =
    persister[ProposalUnvoted]("proposal-unvoted")

  private val proposalQualifiedSerializer: JsonPersister[ProposalQualified, V1] =
    persister[ProposalQualified]("proposal-qualified")

  private val proposalUnqualifiedSerializer: JsonPersister[ProposalUnqualified, V1] =
    persister[ProposalUnqualified]("proposal-unqualified")

  private val proposalSerializer: JsonPersister[Proposal, V1] =
    persister[Proposal]("proposal")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      proposalProposedSerializer,
      proposalViewedSerializer,
      proposalUpdatedSerializer,
      proposalAcceptedSerializer,
      proposalRefusedSerializer,
      proposalVotedSerializer,
      proposalUnvotedSerializer,
      proposalQualifiedSerializer,
      proposalUnqualifiedSerializer,
      proposalSerializer
    )
}

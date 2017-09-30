package org.make.api.proposal

import org.make.core.SprayJsonFormatters
import org.make.core.proposal.Proposal
import org.make.core.proposal.ProposalEvent._
import stamina.V1
import stamina.json._

object ProposalSerializers extends SprayJsonFormatters {

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

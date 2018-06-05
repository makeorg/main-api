package org.make.api.proposal

import org.make.api.proposal.ProposalActor.ProposalState
import org.make.api.proposal.ProposalEvent._
import org.make.api.proposal.PublishedProposalEvent._
import org.make.core.SprayJsonFormatters
import org.make.core.proposal.OrganisationInfo
import spray.json.DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import stamina.json._
import stamina.{V1, V2, V3}

object ProposalSerializers extends SprayJsonFormatters {

  private val proposalProposedSerializer: JsonPersister[ProposalProposed, V2] =
    persister[ProposalProposed, V2](
      "proposal-proposed",
      from[V1].to[V2](
        _.update('language ! set[Option[String]](Some("fr")))
          .update('country ! set[Option[String]](Some("FR")))
      )
    )

  private val proposalViewedSerializer: JsonPersister[ProposalViewed, V1] =
    persister[ProposalViewed]("proposal-viewed")

  private val proposalUpdatedSerializer: JsonPersister[ProposalUpdated, V1] =
    persister[ProposalUpdated]("proposal-updated")

  private val proposalAcceptedSerializer: JsonPersister[ProposalAccepted, V1] =
    persister[ProposalAccepted]("proposal-accepted")

  private val proposalRefusedSerializer: JsonPersister[ProposalRefused, V1] =
    persister[ProposalRefused]("proposal-refused")

  private val proposalPostponedSerializer: JsonPersister[ProposalPostponed, V1] =
    persister[ProposalPostponed]("proposal-postponed")

  private val proposalVotedSerializer: JsonPersister[ProposalVoted, V2] =
    persister[ProposalVoted, V2](
      "proposal-voted",
      from[V1].to[V2](_.update('organisationInfo ! set[Option[OrganisationInfo]](None)))
    )

  private val proposalUnvotedSerializer: JsonPersister[ProposalUnvoted, V2] =
    persister[ProposalUnvoted, V2](
      "proposal-unvoted",
      from[V1].to[V2](_.update('organisationInfo ! set[Option[OrganisationInfo]](None)))
    )

  private val proposalQualifiedSerializer: JsonPersister[ProposalQualified, V1] =
    persister[ProposalQualified]("proposal-qualified")

  private val proposalUnqualifiedSerializer: JsonPersister[ProposalUnqualified, V1] =
    persister[ProposalUnqualified]("proposal-unqualified")

  private val similarProposalsAddedSerializer: JsonPersister[SimilarProposalsAdded, V1] =
    persister[SimilarProposalsAdded]("similar-proposals-added")

  private val proposalLockedSerializer: JsonPersister[ProposalLocked, V1] =
    persister[ProposalLocked]("proposal-locked")

  private val proposalStateSerializer: JsonPersister[ProposalState, V3] =
    persister[ProposalState, V3](
      "proposalState",
      from[V1]
        .to[V2](
          _.update('proposal / 'language ! set[Option[String]](Some("fr")))
            .update('proposal / 'country ! set[Option[String]](Some("FR")))
        )
        .to[V3](_.update('proposal / 'organisations ! set[Seq[OrganisationInfo]](Seq.empty)))
    )

  private val similarProposalRemovedSerializer: JsonPersister[SimilarProposalRemoved, V1] =
    persister[SimilarProposalRemoved]("similar-proposal-removed")

  private val similarProposalsClearedSerializer: JsonPersister[SimilarProposalsCleared, V1] =
    persister[SimilarProposalsCleared]("similar-proposals-cleared")

  private val proposalPatchedSerializer: JsonPersister[ProposalPatched, V2] =
    persister[ProposalPatched, V2](
      "proposal-tags-updated",
      from[V1].to[V2](_.update('proposal / 'organisations ! set[Seq[OrganisationInfo]](Seq.empty)))
    )

  private val proposalAddedToOperationSerializer: JsonPersister[ProposalAddedToOperation, V1] =
    persister[ProposalAddedToOperation]("proposal-added-to-operation")

  private val proposalRemovedFromOperationSerializer: JsonPersister[ProposalRemovedFromOperation, V1] =
    persister[ProposalRemovedFromOperation]("proposal-removed-from-operation")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      proposalProposedSerializer,
      proposalViewedSerializer,
      proposalUpdatedSerializer,
      proposalAcceptedSerializer,
      proposalRefusedSerializer,
      proposalPostponedSerializer,
      proposalVotedSerializer,
      proposalUnvotedSerializer,
      proposalQualifiedSerializer,
      proposalUnqualifiedSerializer,
      proposalLockedSerializer,
      proposalStateSerializer,
      similarProposalsAddedSerializer,
      similarProposalRemovedSerializer,
      similarProposalsClearedSerializer,
      proposalPatchedSerializer,
      proposalAddedToOperationSerializer,
      proposalRemovedFromOperationSerializer
    )
}

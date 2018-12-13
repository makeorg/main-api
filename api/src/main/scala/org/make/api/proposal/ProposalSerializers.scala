/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.proposal

import org.make.api.proposal.ProposalActor.ProposalState
import org.make.api.proposal.ProposalEvent._
import org.make.api.proposal.PublishedProposalEvent._
import org.make.core.SprayJsonFormatters
import org.make.core.proposal.OrganisationInfo
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import stamina.json._
import stamina._

object ProposalSerializers extends SprayJsonFormatters {

  private val proposalProposedSerializer: JsonPersister[ProposalProposed, V3] =
    persister[ProposalProposed, V3](
      "proposal-proposed",
      from[V1]
        .to[V2](
          _.update('language ! set[Option[String]](Some("fr")))
            .update('country ! set[Option[String]](Some("FR")))
        )
        .to[V3] {
          _.update('initialProposal ! set[Boolean](false))
        }
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

  private val proposalVotedSerializer: JsonPersister[ProposalVoted, V3] =
    persister[ProposalVoted, V3](
      "proposal-voted",
      from[V1]
        .to[V2](_.update('organisationInfo ! set[Option[OrganisationInfo]](None)))
        .to[V3] { json =>
          val organisationId =
            json.extract[Option[OrganisationInfo]]('organisationInfo.?).flatMap(_.map(_.organisationId.value))
          organisationId.map { id =>
            json.update('maybeOrganisationId ! set[String](id))
          }.getOrElse(json)
        }
    )

  private val proposalUnvotedSerializer: JsonPersister[ProposalUnvoted, V3] =
    persister[ProposalUnvoted, V3](
      "proposal-unvoted",
      from[V1]
        .to[V2](_.update('organisationInfo ! set[Option[OrganisationInfo]](None)))
        .to[V3] { json =>
          val organisationInfo =
            json.extract[Option[OrganisationInfo]]('organisationInfo.?).flatMap(_.map(_.organisationId.value))
          organisationInfo.map { id =>
            json.update('maybeOrganisationId ! set[String](id))
          }.getOrElse(json)
        }
    )

  private val proposalQualifiedSerializer: JsonPersister[ProposalQualified, V1] =
    persister[ProposalQualified]("proposal-qualified")

  private val proposalUnqualifiedSerializer: JsonPersister[ProposalUnqualified, V1] =
    persister[ProposalUnqualified]("proposal-unqualified")

  private val similarProposalsAddedSerializer: JsonPersister[SimilarProposalsAdded, V1] =
    persister[SimilarProposalsAdded]("similar-proposals-added")

  private val proposalLockedSerializer: JsonPersister[ProposalLocked, V1] =
    persister[ProposalLocked]("proposal-locked")

  private val proposalStateSerializer: JsonPersister[ProposalState, V5] =
    persister[ProposalState, V5](
      "proposalState",
      from[V1]
        .to[V2](
          _.update('proposal / 'language ! set[Option[String]](Some("fr")))
            .update('proposal / 'country ! set[Option[String]](Some("FR")))
        )
        .to[V3](_.update('proposal / 'organisations ! set[Seq[OrganisationInfo]](Seq.empty)))
        .to[V4] { json =>
          val organisationInfos = json.extract[Seq[OrganisationInfo]]('proposal / 'organisations)
          json.update('proposal / 'organisationIds ! set[Seq[UserId]](organisationInfos.map(_.organisationId)))
        }
        .to[V5] {
          _.update('proposal / 'initialProposal ! set[Boolean](false))
        }
    )

  private val similarProposalRemovedSerializer: JsonPersister[SimilarProposalRemoved, V1] =
    persister[SimilarProposalRemoved]("similar-proposal-removed")

  private val similarProposalsClearedSerializer: JsonPersister[SimilarProposalsCleared, V1] =
    persister[SimilarProposalsCleared]("similar-proposals-cleared")

  private val proposalPatchedSerializer: JsonPersister[ProposalPatched, V4] =
    persister[ProposalPatched, V4](
      "proposal-tags-updated",
      from[V1]
        .to[V2](_.update('proposal / 'organisations ! set[Seq[OrganisationInfo]](Seq.empty)))
        .to[V3] { json =>
          val organisationInfos = json.extract[Seq[OrganisationInfo]]('proposal / 'organisations)
          json.update('proposal / 'organisationIds ! set[Seq[UserId]](organisationInfos.map(_.organisationId)))
        }
        .to[V4] {
          _.update('proposal / 'initialProposal ! set[Boolean](false))
        }
    )

  private val proposalAnonymizedSerializer: JsonPersister[ProposalAnonymized, V1] =
    persister[ProposalAnonymized]("proposal-anonymized")

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
      proposalAnonymizedSerializer,
      proposalAddedToOperationSerializer,
      proposalRemovedFromOperationSerializer
    )
}

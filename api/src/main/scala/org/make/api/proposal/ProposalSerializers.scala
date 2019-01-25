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

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import org.make.api.proposal.ProposalActor.ProposalState
import org.make.api.proposal.ProposalEvent._
import org.make.api.proposal.PublishedProposalEvent._
import org.make.core.SprayJsonFormatters
import org.make.core.proposal._
import org.make.core.user.UserId
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import spray.json.DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import stamina._
import stamina.json._

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

  private val proposalVotesVerifiedUpdatedSerializer: JsonPersister[ProposalVotesVerifiedUpdated, V1] =
    persister[ProposalVotesVerifiedUpdated]("proposal-votes-verified-updated")

  private val proposalVotedSerializer: JsonPersister[ProposalVoted, V4] =
    persister[ProposalVoted, V4](
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
        .to[V4](_.update('voteTrust ! set[String]("trusted")))
    )

  private val proposalUnvotedSerializer: JsonPersister[ProposalUnvoted, V4] =
    persister[ProposalUnvoted, V4](
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
        .to[V4](_.update('voteTrust ! set[String]("trusted")))
    )

  private val proposalQualifiedSerializer: JsonPersister[ProposalQualified, V2] =
    persister[ProposalQualified, V2](
      "proposal-qualified",
      from[V1].to[V2](_.update('voteTrust ! set[String]("trusted")))
    )

  private val proposalUnqualifiedSerializer: JsonPersister[ProposalUnqualified, V2] =
    persister[ProposalUnqualified, V2](
      "proposal-unqualified",
      from[V1].to[V2](_.update('voteTrust ! set[String]("trusted")))
    )

  private val similarProposalsAddedSerializer: JsonPersister[SimilarProposalsAdded, V1] =
    persister[SimilarProposalsAdded]("similar-proposals-added")

  private val proposalLockedSerializer: JsonPersister[ProposalLocked, V1] =
    persister[ProposalLocked]("proposal-locked")

  private val proposalStateSerializer: JsonPersister[ProposalState, V6] =
    persister[ProposalState, V6](
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
        .to[V6] { json =>
          case class QualificationOld(key: QualificationKey, count: Int)
          case class VoteOld(key: VoteKey, count: Int, qualifications: Seq[QualificationOld])
          object QualificationOld {
            implicit val decoder: Decoder[QualificationOld] = deriveDecoder[QualificationOld]
            implicit val voteFormatter: RootJsonFormat[QualificationOld] =
              DefaultJsonProtocol.jsonFormat2(QualificationOld.apply)
          }
          object VoteOld {
            implicit val decoder: Decoder[VoteOld] = deriveDecoder[VoteOld]
            implicit val voteFormatter: RootJsonFormat[VoteOld] =
              DefaultJsonProtocol.jsonFormat3(VoteOld.apply)
          }

          val votes: Seq[Vote] = json
            .extract[Seq[VoteOld]]('proposal / 'votes)
            .map(vote => {
              val qualifications: Seq[Qualification] = vote.qualifications
                .map(qualification => Qualification(qualification.key, qualification.count, qualification.count))
              Vote(vote.key, vote.count, vote.count, qualifications)
            })
          json.update('proposal / 'votes, votes)
        }
    )

  private val similarProposalRemovedSerializer: JsonPersister[SimilarProposalRemoved, V1] =
    persister[SimilarProposalRemoved]("similar-proposal-removed")

  private val similarProposalsClearedSerializer: JsonPersister[SimilarProposalsCleared, V1] =
    persister[SimilarProposalsCleared]("similar-proposals-cleared")

  private val proposalPatchedSerializer: JsonPersister[ProposalPatched, V5] =
    persister[ProposalPatched, V5](
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
        .to[V5] { json =>
          case class QualificationOld(key: QualificationKey, count: Int)
          case class VoteOld(key: VoteKey, count: Int, qualifications: Seq[QualificationOld])
          object QualificationOld {
            implicit val decoder: Decoder[QualificationOld] = deriveDecoder[QualificationOld]
            implicit val voteFormatter: RootJsonFormat[QualificationOld] =
              DefaultJsonProtocol.jsonFormat2(QualificationOld.apply)
          }
          object VoteOld {
            implicit val decoder: Decoder[VoteOld] = deriveDecoder[VoteOld]
            implicit val voteFormatter: RootJsonFormat[VoteOld] =
              DefaultJsonProtocol.jsonFormat3(VoteOld.apply)
          }

          val votes: Seq[Vote] = json
            .extract[Seq[VoteOld]]('proposal / 'votes)
            .map(vote => {
              val qualifications: Seq[Qualification] = vote.qualifications
                .map(qualification => Qualification(qualification.key, qualification.count, qualification.count))
              Vote(vote.key, vote.count, vote.count, qualifications)
            })
          json.update('proposal / 'votes, votes)
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
      proposalVotesVerifiedUpdatedSerializer,
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

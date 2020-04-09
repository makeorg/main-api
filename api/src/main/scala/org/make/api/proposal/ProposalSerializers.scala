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
import org.make.core.proposal._
import org.make.core.user.UserId
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import spray.json.DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import stamina._
import stamina.json._

object ProposalSerializers extends SprayJsonFormatters {

  private val proposalProposedSerializer: JsonPersister[ProposalProposed, V4] =
    persister[ProposalProposed, V4](
      "proposal-proposed",
      from[V1]
        .to[V2](
          _.update("language" ! set[Option[String]](Some("fr")))
            .update("country" ! set[Option[String]](Some("FR")))
        )
        .to[V3] {
          _.update("initialProposal" ! set[Boolean](false))
        }
        .to[V4](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalViewedSerializer: JsonPersister[ProposalViewed, V2] =
    persister[ProposalViewed, V2](
      "proposal-viewed",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalUpdatedSerializer: JsonPersister[ProposalUpdated, V2] =
    persister[ProposalUpdated, V2](
      "proposal-updated",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalAcceptedSerializer: JsonPersister[ProposalAccepted, V2] =
    persister[ProposalAccepted, V2](
      "proposal-accepted",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalRefusedSerializer: JsonPersister[ProposalRefused, V2] =
    persister[ProposalRefused, V2](
      "proposal-refused",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalPostponedSerializer: JsonPersister[ProposalPostponed, V2] =
    persister[ProposalPostponed, V2](
      "proposal-postponed",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalVotesVerifiedUpdatedSerializer: JsonPersister[ProposalVotesVerifiedUpdated, V2] =
    persister[ProposalVotesVerifiedUpdated, V2](
      "proposal-votes-verified-updated",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalVotesUpdatedSerializer: JsonPersister[ProposalVotesUpdated, V1] =
    persister[ProposalVotesUpdated]("proposal-votes-updated")

  private val proposalVotedSerializer: JsonPersister[ProposalVoted, V5] =
    persister[ProposalVoted, V5](
      "proposal-voted",
      from[V1]
        .to[V2](_.update("organisationInfo" ! set[Option[OrganisationInfo]](None)))
        .to[V3] { json =>
          val organisationId =
            json.extract[Option[OrganisationInfo]]("organisationInfo".?).flatMap(_.map(_.organisationId.value))
          organisationId.map { id =>
            json.update("maybeOrganisationId" ! set[String](id))
          }.getOrElse(json)
        }
        .to[V4](_.update("voteTrust" ! set[String]("trusted")))
        .to[V5](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalUnvotedSerializer: JsonPersister[ProposalUnvoted, V5] =
    persister[ProposalUnvoted, V5](
      "proposal-unvoted",
      from[V1]
        .to[V2](_.update("organisationInfo" ! set[Option[OrganisationInfo]](None)))
        .to[V3] { json =>
          val organisationInfo =
            json.extract[Option[OrganisationInfo]]("organisationInfo".?).flatMap(_.map(_.organisationId.value))
          organisationInfo.map { id =>
            json.update("maybeOrganisationId" ! set[String](id))
          }.getOrElse(json)
        }
        .to[V4](_.update("voteTrust" ! set[String]("trusted")))
        .to[V5](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalQualifiedSerializer: JsonPersister[ProposalQualified, V3] =
    persister[ProposalQualified, V3](
      "proposal-qualified",
      from[V1]
        .to[V2](_.update("voteTrust" ! set[String]("trusted")))
        .to[V3](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalUnqualifiedSerializer: JsonPersister[ProposalUnqualified, V3] =
    persister[ProposalUnqualified, V3](
      "proposal-unqualified",
      from[V1]
        .to[V2](_.update("voteTrust" ! set[String]("trusted")))
        .to[V3](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val similarProposalsAddedSerializer: JsonPersister[SimilarProposalsAdded, V2] =
    persister[SimilarProposalsAdded, V2](
      "similar-proposals-added",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalLockedSerializer: JsonPersister[ProposalLocked, V2] =
    persister[ProposalLocked, V2](
      "proposal-locked",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalStateSerializer: JsonPersister[ProposalState, V8] = {
    case class QualificationV5(key: QualificationKey, count: Int)
    implicit val qualificationV5Formatter: RootJsonFormat[QualificationV5] =
      DefaultJsonProtocol.jsonFormat2(QualificationV5.apply)

    case class VoteV5(key: VoteKey, count: Int, qualifications: Seq[QualificationV5])
    implicit val voteV5Formatter: RootJsonFormat[VoteV5] =
      DefaultJsonProtocol.jsonFormat3(VoteV5.apply)

    case class QualificationV6(key: QualificationKey, count: Int, countVerified: Int)
    implicit val qualificationV6formatter: RootJsonFormat[QualificationV6] =
      DefaultJsonProtocol.jsonFormat3(QualificationV6.apply)

    case class VoteV6(key: VoteKey, count: Int, countVerified: Int, qualifications: Seq[QualificationV6])
    implicit val voteV6formatter: RootJsonFormat[VoteV6] =
      DefaultJsonProtocol.jsonFormat4(VoteV6.apply)

    final case class QualificationV8(key: QualificationKey,
                                     count: Int,
                                     countVerified: Int,
                                     countSequence: Int,
                                     countSegment: Int)
    implicit val qualificationV8formatter: RootJsonFormat[QualificationV8] =
      DefaultJsonProtocol.jsonFormat5(QualificationV8.apply)

    final case class VoteV8(key: VoteKey,
                            count: Int,
                            countVerified: Int,
                            countSequence: Int,
                            countSegment: Int,
                            qualifications: Seq[QualificationV8])

    implicit val voteV8formatter: RootJsonFormat[VoteV8] =
      DefaultJsonProtocol.jsonFormat6(VoteV8.apply)

    persister[ProposalState, V8](
      "proposalState",
      from[V1]
        .to[V2](
          _.update("proposal" / "language" ! set[Option[String]](Some("fr")))
            .update("proposal" / "country" ! set[Option[String]](Some("FR")))
        )
        .to[V3](_.update("proposal" / "organisations" ! set[Seq[OrganisationInfo]](Seq.empty)))
        .to[V4] { json =>
          val organisationInfos = json.extract[Seq[OrganisationInfo]]("proposal" / "organisations")
          json.update("proposal" / "organisationIds" ! set[Seq[UserId]](organisationInfos.map(_.organisationId)))
        }
        .to[V5] {
          _.update("proposal" / "initialProposal" ! set[Boolean](false))
        }
        .to[V6] { json =>
          val votes: Seq[VoteV6] = json
            .extract[Seq[VoteV5]]("proposal" / "votes")
            .map(vote => {
              val qualifications: Seq[QualificationV6] = vote.qualifications
                .map(qualification => QualificationV6(qualification.key, qualification.count, qualification.count))
              VoteV6(vote.key, vote.count, vote.count, qualifications)
            })
          json.update("proposal" / "votes", votes)
        }
        .to[V7](_.update("proposal" / "creationContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V8] { json =>
          val votes: Seq[VoteV8] = json
            .extract[Seq[VoteV6]]("proposal" / "votes")
            .map(vote => {
              val qualifications: Seq[QualificationV8] = vote.qualifications
                .map(
                  qualification =>
                    QualificationV8(
                      key = qualification.key,
                      count = qualification.count,
                      countVerified = qualification.countVerified,
                      countSequence = qualification.countVerified,
                      countSegment = 0
                  )
                )
              VoteV8(
                key = vote.key,
                count = vote.count,
                countVerified = vote.countVerified,
                countSequence = vote.countVerified,
                countSegment = 0,
                qualifications = qualifications
              )
            })
          json.update("proposal" / "votes", votes)
        }
    )
  }

  private val similarProposalRemovedSerializer: JsonPersister[SimilarProposalRemoved, V2] =
    persister[SimilarProposalRemoved, V2](
      "similar-proposal-removed",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val similarProposalsClearedSerializer: JsonPersister[SimilarProposalsCleared, V2] =
    persister[SimilarProposalsCleared, V2](
      "similar-proposals-cleared",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalPatchedSerializer: JsonPersister[ProposalPatched, V7] = {

    case class QualificationV4(key: QualificationKey, count: Int)
    implicit val qualificationV4Formatter: RootJsonFormat[QualificationV4] =
      DefaultJsonProtocol.jsonFormat2(QualificationV4.apply)

    case class VoteV4(key: VoteKey, count: Int, qualifications: Seq[QualificationV4])
    implicit val voteV4Formatter: RootJsonFormat[VoteV4] =
      DefaultJsonProtocol.jsonFormat3(VoteV4.apply)

    case class QualificationV5(key: QualificationKey, count: Int, countVerified: Int)
    implicit val qualificationV5Formatter: RootJsonFormat[QualificationV5] =
      DefaultJsonProtocol.jsonFormat3(QualificationV5.apply)

    case class VoteV5(key: VoteKey, count: Int, countVerified: Int, qualifications: Seq[QualificationV5])
    implicit val voteV5Formatter: RootJsonFormat[VoteV5] =
      DefaultJsonProtocol.jsonFormat4(VoteV5.apply)

    final case class QualificationV7(key: QualificationKey,
                                     count: Int,
                                     countVerified: Int,
                                     countSequence: Int,
                                     countSegment: Int)
    implicit val qualificationV7formatter: RootJsonFormat[QualificationV7] =
      DefaultJsonProtocol.jsonFormat5(QualificationV7.apply)

    final case class VoteV7(key: VoteKey,
                            count: Int,
                            countVerified: Int,
                            countSequence: Int,
                            countSegment: Int,
                            qualifications: Seq[QualificationV7])

    implicit val voteV7formatter: RootJsonFormat[VoteV7] =
      DefaultJsonProtocol.jsonFormat6(VoteV7.apply)

    persister[ProposalPatched, V7](
      "proposal-tags-updated",
      from[V1]
        .to[V2](_.update("proposal" / "organisations" ! set[Seq[OrganisationInfo]](Seq.empty)))
        .to[V3] { json =>
          val organisationInfos = json.extract[Seq[OrganisationInfo]]("proposal" / "organisations")
          json.update("proposal" / "organisationIds" ! set[Seq[UserId]](organisationInfos.map(_.organisationId)))
        }
        .to[V4] {
          _.update("proposal" / "initialProposal" ! set[Boolean](false))
        }
        .to[V5] { json =>
          val votes: Seq[VoteV5] = json
            .extract[Seq[VoteV4]]("proposal" / "votes")
            .map(vote => {
              val qualifications: Seq[QualificationV5] = vote.qualifications
                .map(qualification => QualificationV5(qualification.key, qualification.count, qualification.count))
              VoteV5(vote.key, vote.count, vote.count, qualifications)
            })
          json.update("proposal" / "votes", votes)
        }
        .to[V6](
          _.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty))
            .update("proposal" / "creationContext" / "customData" ! set[Map[String, String]](Map.empty))
        )
        .to[V7] { json =>
          val votes: Seq[VoteV7] = json
            .extract[Seq[VoteV5]]("proposal" / "votes")
            .map(vote => {
              val qualifications: Seq[QualificationV7] = vote.qualifications
                .map(
                  qualification =>
                    QualificationV7(
                      key = qualification.key,
                      count = qualification.countVerified,
                      countVerified = qualification.countVerified,
                      countSequence = qualification.count,
                      countSegment = 0
                  )
                )
              VoteV7(
                key = vote.key,
                count = vote.count,
                countVerified = vote.countVerified,
                countSequence = vote.countVerified,
                countSegment = 0,
                qualifications = qualifications
              )
            })
          json.update("proposal" / "votes", votes)
        }
    )
  }

  private val proposalAnonymizedSerializer: JsonPersister[ProposalAnonymized, V2] =
    persister[ProposalAnonymized, V2](
      "proposal-anonymized",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalAddedToOperationSerializer: JsonPersister[ProposalAddedToOperation, V2] =
    persister[ProposalAddedToOperation, V2](
      "proposal-added-to-operation",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalRemovedFromOperationSerializer: JsonPersister[ProposalRemovedFromOperation, V2] =
    persister[ProposalRemovedFromOperation, V2](
      "proposal-removed-from-operation",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
    )

  private val proposalSerializer: JsonPersister[Proposal, V1] =
    persister[Proposal]("proposal-entity")

  val serializers: Seq[JsonPersister[_, _]] =
    Seq(
      proposalProposedSerializer,
      proposalViewedSerializer,
      proposalUpdatedSerializer,
      proposalAcceptedSerializer,
      proposalRefusedSerializer,
      proposalPostponedSerializer,
      proposalVotesVerifiedUpdatedSerializer,
      proposalVotesUpdatedSerializer,
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
      proposalRemovedFromOperationSerializer,
      proposalSerializer
    )
}

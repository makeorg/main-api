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

import org.make.api.proposal.ProposalActor.State
import org.make.api.proposal.ProposalEvent._
import org.make.api.proposal.PublishedProposalEvent._
import org.make.api.technical.MakeEventSerializer
import org.make.api.technical.security.SecurityConfiguration
import org.make.core.SprayJsonFormatters
import org.make.core.proposal._
import org.make.core.user.UserId
import spray.json.{DefaultJsonProtocol, JsObject, RootJsonFormat}
import spray.json.DefaultJsonProtocol._
import spray.json.lenses.JsonLenses._
import stamina._
import stamina.json._

final class ProposalSerializers(securityConfiguration: SecurityConfiguration) extends SprayJsonFormatters {

  private val proposalProposedSerializer: JsonPersister[ProposalProposed, V5] =
    persister[ProposalProposed, V5](
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
        .to[V5](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalViewedSerializer: JsonPersister[ProposalViewed, V3] =
    persister[ProposalViewed, V3](
      "proposal-viewed",
      from[V1]
        .to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalUpdatedSerializer: JsonPersister[ProposalUpdated, V3] =
    persister[ProposalUpdated, V3](
      "proposal-updated",
      from[V1]
        .to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalAcceptedSerializer: JsonPersister[ProposalAccepted, V3] =
    persister[ProposalAccepted, V3](
      "proposal-accepted",
      from[V1]
        .to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalRefusedSerializer: JsonPersister[ProposalRefused, V3] =
    persister[ProposalRefused, V3](
      "proposal-refused",
      from[V1]
        .to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalPostponedSerializer: JsonPersister[ProposalPostponed, V3] =
    persister[ProposalPostponed, V3](
      "proposal-postponed",
      from[V1]
        .to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalVotesVerifiedUpdatedSerializer: JsonPersister[ProposalVotesVerifiedUpdated, V3] =
    persister[ProposalVotesVerifiedUpdated, V3](
      "proposal-votes-verified-updated",
      from[V1]
        .to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalVotesUpdatedSerializer: JsonPersister[ProposalVotesUpdated, V2] =
    persister[ProposalVotesUpdated, V2](
      "proposal-votes-updated",
      from[V1].to[V2](
        _.update(
          "requestContext" ! modify[JsObject](
            MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
          )
        )
      )
    )

  private val proposalVotedSerializer: JsonPersister[ProposalVoted, V6] =
    persister[ProposalVoted, V6](
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
        .to[V6](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalUnvotedSerializer: JsonPersister[ProposalUnvoted, V6] =
    persister[ProposalUnvoted, V6](
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
        .to[V6](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalQualifiedSerializer: JsonPersister[ProposalQualified, V4] =
    persister[ProposalQualified, V4](
      "proposal-qualified",
      from[V1]
        .to[V2](_.update("voteTrust" ! set[String]("trusted")))
        .to[V3](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalUnqualifiedSerializer: JsonPersister[ProposalUnqualified, V4] =
    persister[ProposalUnqualified, V4](
      "proposal-unqualified",
      from[V1]
        .to[V2](_.update("voteTrust" ! set[String]("trusted")))
        .to[V3](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V4](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val similarProposalsAddedSerializer: JsonPersister[SimilarProposalsAdded, V3] =
    persister[SimilarProposalsAdded, V3](
      "similar-proposals-added",
      from[V1]
        .to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalLockedSerializer: JsonPersister[ProposalLocked, V3] =
    persister[ProposalLocked, V3](
      "proposal-locked",
      from[V1]
        .to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](
          _.update(
            "requestContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )

  private val proposalStateSerializer: JsonPersister[State, V10] = {
    final case class QualificationV5(key: QualificationKey, count: Int)
    implicit val qualificationV5Formatter: RootJsonFormat[QualificationV5] =
      DefaultJsonProtocol.jsonFormat2(QualificationV5.apply)

    final case class VoteV5(key: VoteKey, count: Int, qualifications: Seq[QualificationV5])
    implicit val voteV5Formatter: RootJsonFormat[VoteV5] =
      DefaultJsonProtocol.jsonFormat3(VoteV5.apply)

    final case class QualificationV6(key: QualificationKey, count: Int, countVerified: Int)
    implicit val qualificationV6formatter: RootJsonFormat[QualificationV6] =
      DefaultJsonProtocol.jsonFormat3(QualificationV6.apply)

    final case class VoteV6(key: VoteKey, count: Int, countVerified: Int, qualifications: Seq[QualificationV6])
    implicit val voteV6formatter: RootJsonFormat[VoteV6] =
      DefaultJsonProtocol.jsonFormat4(VoteV6.apply)

    final case class QualificationV8(
      key: QualificationKey,
      count: Int,
      countVerified: Int,
      countSequence: Int,
      countSegment: Int
    )
    implicit val qualificationV8formatter: RootJsonFormat[QualificationV8] =
      DefaultJsonProtocol.jsonFormat5(QualificationV8.apply)

    final case class VoteV8(
      key: VoteKey,
      count: Int,
      countVerified: Int,
      countSequence: Int,
      countSegment: Int,
      qualifications: Seq[QualificationV8]
    )

    implicit val voteV8formatter: RootJsonFormat[VoteV8] =
      DefaultJsonProtocol.jsonFormat6(VoteV8.apply)

    persister[State, V10](
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
        .to[V9] { json =>
          json.update("proposal" / "keywords", Seq.empty[ProposalKeyword])
        }
        // format: off
        .to[V10](
          _.update(
            "proposal" / "creationContext" ! modify[JsObject](
              MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)
            )
          )
        )
    )
  }

  private val similarProposalRemovedSerializer: JsonPersister[SimilarProposalRemoved, V3] =
    persister[SimilarProposalRemoved, V3](
      "similar-proposal-removed",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](_.update("requestContext" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))))
    )

  private val similarProposalsClearedSerializer: JsonPersister[SimilarProposalsCleared, V3] =
    persister[SimilarProposalsCleared, V3](
      "similar-proposals-cleared",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](_.update("requestContext" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))))
    )

  private val proposalPatchedSerializer: JsonPersister[ProposalPatched, V9] = {

    final case class QualificationV4(key: QualificationKey, count: Int)
    implicit val qualificationV4Formatter: RootJsonFormat[QualificationV4] =
      DefaultJsonProtocol.jsonFormat2(QualificationV4.apply)

    final case class VoteV4(key: VoteKey, count: Int, qualifications: Seq[QualificationV4])
    implicit val voteV4Formatter: RootJsonFormat[VoteV4] =
      DefaultJsonProtocol.jsonFormat3(VoteV4.apply)

    final case class QualificationV5(key: QualificationKey, count: Int, countVerified: Int)
    implicit val qualificationV5Formatter: RootJsonFormat[QualificationV5] =
      DefaultJsonProtocol.jsonFormat3(QualificationV5.apply)

    final case class VoteV5(key: VoteKey, count: Int, countVerified: Int, qualifications: Seq[QualificationV5])
    implicit val voteV5Formatter: RootJsonFormat[VoteV5] =
      DefaultJsonProtocol.jsonFormat4(VoteV5.apply)

    final case class QualificationV7(
      key: QualificationKey,
      count: Int,
      countVerified: Int,
      countSequence: Int,
      countSegment: Int
    )
    implicit val qualificationV7formatter: RootJsonFormat[QualificationV7] =
      DefaultJsonProtocol.jsonFormat5(QualificationV7.apply)

    final case class VoteV7(
      key: VoteKey,
      count: Int,
      countVerified: Int,
      countSequence: Int,
      countSegment: Int,
      qualifications: Seq[QualificationV7]
    )

    implicit val voteV7formatter: RootJsonFormat[VoteV7] =
      DefaultJsonProtocol.jsonFormat6(VoteV7.apply)

    persister[ProposalPatched, V9](
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
        .to[V8] { json =>
          json.update("proposal" / "keywords", Seq.empty[ProposalKeyword])
        }
        .to[V9](_.update("requestContext" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)))
          .update("proposal" / "creationContext" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt)))
        )
    )
  }

  private val proposalAnonymizedSerializer: JsonPersister[ProposalAnonymized, V3] =
    persister[ProposalAnonymized, V3](
      "proposal-anonymized",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](_.update("requestContext" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))))
    )

  private val proposalAddedToOperationSerializer: JsonPersister[ProposalAddedToOperation, V3] =
    persister[ProposalAddedToOperation, V3](
      "proposal-added-to-operation",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](_.update("requestContext" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))))
    )

  private val proposalRemovedFromOperationSerializer: JsonPersister[ProposalRemovedFromOperation, V3] =
    persister[ProposalRemovedFromOperation, V3](
      "proposal-removed-from-operation",
      from[V1].to[V2](_.update("requestContext" / "customData" ! set[Map[String, String]](Map.empty)))
        .to[V3](_.update("requestContext" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))))
    )

  private val proposalKeywordsSetSerializer: JsonPersister[ProposalKeywordsSet, V2] =
    persister[ProposalKeywordsSet, V2]("proposal-keywords-set",
      from[V1].to[V2](_.update("requestContext" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))))
  )

  private val proposalSerializer: JsonPersister[Proposal, V3] =
    persister[Proposal, V3]("proposal-entity", from[V1].to[V2](_.update("keywords", Seq.empty[ProposalKeyword]))
      .to[V3](_.update("creationContext" ! modify[JsObject](MakeEventSerializer.setIpAddressAndHash(securityConfiguration.secureHashSalt))))
    )

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
      proposalKeywordsSetSerializer,
      proposalSerializer
    )
}

object ProposalSerializers {
  def apply(securityConfiguration: SecurityConfiguration): ProposalSerializers =
    new ProposalSerializers(securityConfiguration)
}

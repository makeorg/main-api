package org.make.core.proposal

import java.time.ZonedDateTime

import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import io.circe.generic.semiauto._
import org.make.core.SprayJsonFormatters._
import org.make.core.operation.OperationId
import org.make.core.reference.{IdeaId, LabelId, TagId, ThemeId}
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, RequestContext, StringValue, Timestamped}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

final case class Proposal(proposalId: ProposalId,
                          slug: String,
                          content: String,
                          author: UserId,
                          labels: Seq[LabelId],
                          theme: Option[ThemeId] = None,
                          status: ProposalStatus = ProposalStatus.Pending,
                          refusalReason: Option[String] = None,
                          tags: Seq[TagId] = Seq.empty,
                          votes: Seq[Vote],
                          creationContext: RequestContext,
                          similarProposals: Seq[ProposalId] = Seq.empty,
                          idea: Option[IdeaId] = None,
                          operation: Option[OperationId] = None,
                          override val createdAt: Option[ZonedDateTime],
                          override val updatedAt: Option[ZonedDateTime],
                          events: List[ProposalAction])
    extends MakeSerializable
    with Timestamped

object Proposal {
  implicit val proposalFormatter: RootJsonFormat[Proposal] =
    DefaultJsonProtocol.jsonFormat17(Proposal.apply)

}

final case class ProposalId(value: String) extends StringValue

object ProposalId {
  implicit lazy val proposalIdEncoder: Encoder[ProposalId] =
    (a: ProposalId) => Json.fromString(a.value)
  implicit lazy val proposalIdDecoder: Decoder[ProposalId] =
    Decoder.decodeString.map(ProposalId(_))

  implicit val proposalIdFormatter: JsonFormat[ProposalId] = new JsonFormat[ProposalId] {
    override def read(json: JsValue): ProposalId = json match {
      case JsString(s) => ProposalId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ProposalId): JsValue = {
      JsString(obj.value)
    }
  }

}

final case class AuthorInfo(userId: UserId, firstName: Option[String], postalCode: Option[String], age: Option[Int])

final case class ProposalAction(date: ZonedDateTime, user: UserId, actionType: String, arguments: Map[String, String])

object ProposalAction {
  implicit val proposalActionFormatter: RootJsonFormat[ProposalAction] =
    DefaultJsonProtocol.jsonFormat4(ProposalAction.apply)
}

sealed trait ProposalActionType { val name: String }
case object ProposalProposeAction extends ProposalActionType { override val name: String = "propose" }
case object ProposalUpdateAction extends ProposalActionType { override val name: String = "update" }
case object ProposalAcceptAction extends ProposalActionType { override val name: String = "accept" }
case object ProposalVoteAction extends ProposalActionType { override val name: String = "vote" }
case object ProposalUnvoteAction extends ProposalActionType { override val name: String = "unvote" }
case object ProposalQualifyAction extends ProposalActionType { override val name: String = "qualify" }
case object ProposalUnqualifyAction extends ProposalActionType { override val name: String = "unqualify" }

sealed trait QualificationKey { val shortName: String }

object QualificationKey extends StrictLogging {
  val qualificationKeys: Map[String, QualificationKey] = Map(
    LikeIt.shortName -> LikeIt,
    Doable.shortName -> Doable,
    PlatitudeAgree.shortName -> PlatitudeAgree,
    NoWay.shortName -> NoWay,
    Impossible.shortName -> Impossible,
    PlatitudeDisagree.shortName -> PlatitudeDisagree,
    DoNotUnderstand.shortName -> DoNotUnderstand,
    NoOpinion.shortName -> NoOpinion,
    DoNotCare.shortName -> DoNotCare
  )

  implicit val qualificationKeyEncoder: Encoder[QualificationKey] =
    (qualificationKey: QualificationKey) => Json.fromString(qualificationKey.shortName)
  implicit val qualificationKeyDecoder: Decoder[QualificationKey] =
    Decoder.decodeString.map(
      qualificationKey =>
        QualificationKey
          .matchQualificationKey(qualificationKey)
          .getOrElse(throw new IllegalArgumentException(s"$qualificationKey is not a QualificationKey"))
    )

  implicit val qualificationKeyFormatter: JsonFormat[QualificationKey] = new JsonFormat[QualificationKey] {
    override def read(json: JsValue): QualificationKey = json match {
      case JsString(s) =>
        QualificationKey.qualificationKeys.getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
      case other => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: QualificationKey): JsValue = {
      JsString(obj.shortName)
    }
  }

  def matchQualificationKey(qualificationKey: String): Option[QualificationKey] = {
    val maybeQualificationKey = qualificationKeys.get(qualificationKey)
    if (maybeQualificationKey.isEmpty) {
      logger.warn(s"$qualificationKey is not a qualification key")
    }
    maybeQualificationKey
  }

  case object LikeIt extends QualificationKey { override val shortName: String = "likeIt" }
  case object Doable extends QualificationKey { override val shortName: String = "doable" }
  case object PlatitudeAgree extends QualificationKey { override val shortName: String = "platitudeAgree" }
  case object NoWay extends QualificationKey { override val shortName: String = "noWay" }
  case object Impossible extends QualificationKey { override val shortName: String = "impossible" }
  case object PlatitudeDisagree extends QualificationKey { override val shortName: String = "platitudeDisagree" }
  case object DoNotUnderstand extends QualificationKey { override val shortName: String = "doNotUnderstand" }
  case object NoOpinion extends QualificationKey { override val shortName: String = "noOpinion" }
  case object DoNotCare extends QualificationKey { override val shortName: String = "doNotCare" }
}

final case class Qualification(key: QualificationKey, count: Int = 0)

object Qualification {
  implicit val encoder: ObjectEncoder[Qualification] = deriveEncoder[Qualification]
  implicit val decoder: Decoder[Qualification] = deriveDecoder[Qualification]

  implicit val qualificationFormatter: RootJsonFormat[Qualification] =
    DefaultJsonProtocol.jsonFormat2(Qualification.apply)

}

final case class Vote(key: VoteKey, count: Int = 0, qualifications: Seq[Qualification])

object Vote {
  implicit val encoder: ObjectEncoder[Vote] = deriveEncoder[Vote]
  implicit val decoder: Decoder[Vote] = deriveDecoder[Vote]

  implicit val voteFormatter: RootJsonFormat[Vote] =
    DefaultJsonProtocol.jsonFormat3(Vote.apply)
}

sealed trait VoteKey { val shortName: String }

object VoteKey extends StrictLogging {
  val voteKeys: Map[String, VoteKey] =
    Map(Agree.shortName -> Agree, Disagree.shortName -> Disagree, Neutral.shortName -> Neutral)

  implicit lazy val voteKeyEncoder: Encoder[VoteKey] =
    (voteKey: VoteKey) => Json.fromString(voteKey.shortName)
  implicit lazy val voteKeyDecoder: Decoder[VoteKey] =
    Decoder.decodeString.map(
      voteKey =>
        VoteKey.matchVoteKey(voteKey).getOrElse(throw new IllegalArgumentException(s"$voteKey is not a VoteKey"))
    )

  implicit val voteKeyFormatter: JsonFormat[VoteKey] = new JsonFormat[VoteKey] {
    override def read(json: JsValue): VoteKey = json match {
      case JsString(s) => VoteKey.voteKeys.getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: VoteKey): JsValue = {
      JsString(obj.shortName)
    }
  }

  def matchVoteKey(voteKey: String): Option[VoteKey] = {
    val maybeVoteKey = voteKeys.get(voteKey)
    if (maybeVoteKey.isEmpty) {
      logger.warn(s"$voteKey is not a voteKey")
    }
    maybeVoteKey
  }

  case object Agree extends VoteKey { override val shortName: String = "agree" }
  case object Disagree extends VoteKey { override val shortName: String = "disagree" }
  case object Neutral extends VoteKey { override val shortName: String = "neutral" }
}

sealed trait ProposalStatus {
  def shortName: String
}

object ProposalStatus {
  val statusMap: Map[String, ProposalStatus] =
    Map(
      Pending.shortName -> Pending,
      Postponed.shortName -> Postponed,
      Accepted.shortName -> Accepted,
      Refused.shortName -> Refused,
      Archived.shortName -> Archived
    )

  implicit lazy val proposalStatusEncoder: Encoder[ProposalStatus] = (status: ProposalStatus) =>
    Json.fromString(status.shortName)
  implicit lazy val proposalStatusDecoder: Decoder[ProposalStatus] =
    Decoder.decodeString.emap { value: String =>
      statusMap.get(value) match {
        case Some(status) => Right(status)
        case None         => Left(s"$value is not a proposal status")
      }
    }

  implicit val proposalStatusFormatted: JsonFormat[ProposalStatus] = new JsonFormat[ProposalStatus] {
    override def read(json: JsValue): ProposalStatus = json match {
      case JsString(s) => ProposalStatus.statusMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ProposalStatus): JsValue = {
      JsString(obj.shortName)
    }
  }

  case object Pending extends ProposalStatus {
    override val shortName = "Pending"
  }

  case object Accepted extends ProposalStatus {
    override val shortName = "Accepted"
  }

  case object Refused extends ProposalStatus {
    override val shortName = "Refused"
  }

  case object Postponed extends ProposalStatus {
    override val shortName = "Postponed"
  }

  case object Archived extends ProposalStatus {
    override val shortName = "Archived"
  }
}

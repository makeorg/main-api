package org.make.core.proposal

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder, Json}
import org.make.core.proposal.indexed.Vote
import org.make.core.reference.{LabelId, TagId, ThemeId}
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, RequestContext, StringValue, Timestamped}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}
import spray.json.DefaultJsonProtocol._
import org.make.core.SprayJsonFormatters._

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
                          override val createdAt: Option[ZonedDateTime],
                          override val updatedAt: Option[ZonedDateTime],
                          events: List[ProposalAction])
    extends MakeSerializable
    with Timestamped

object Proposal {
  implicit val proposalFormatter: RootJsonFormat[Proposal] =
    DefaultJsonProtocol.jsonFormat14(Proposal.apply)

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

sealed trait ProposalStatus {
  def shortName: String
}

object ProposalStatus {
  val statusMap: Map[String, ProposalStatus] =
    Map(
      Pending.shortName -> Pending,
      Accepted.shortName -> Accepted,
      Refused.shortName -> Refused,
      Archived.shortName -> Archived
    )

  implicit lazy val proposalStatusEncoder: Encoder[ProposalStatus] = (status: ProposalStatus) =>
    Json.fromString(status.shortName)
  implicit lazy val proposalStatusDecoder: Decoder[ProposalStatus] =
    Decoder.decodeString.emap { value: String =>
      statusMap.get(value) match {
        case Some(profile) => Right(profile)
        case None          => Left(s"$value is not a proposal status")
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

  case object Archived extends ProposalStatus {
    override val shortName = "Archived"
  }
}

package org.make.core.proposal

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder, Json}
import org.make.core.tag.TagId
import org.make.core.theme.ThemeId
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, RequestContext, StringValue, Timestamped}

case class Proposal(proposalId: ProposalId,
                    slug: String,
                    content: String,
                    author: UserId,
                    theme: Option[ThemeId] = None,
                    status: ProposalStatus = ProposalStatus.Pending,
                    tags: Seq[TagId] = Seq(),
                    creationContext: RequestContext,
                    override val createdAt: Option[ZonedDateTime],
                    override val updatedAt: Option[ZonedDateTime])
    extends MakeSerializable
    with Timestamped

case class ProposalId(value: String) extends StringValue

object ProposalId {
  implicit lazy val proposalIdEncoder: Encoder[ProposalId] =
    (a: ProposalId) => Json.fromString(a.value)
  implicit lazy val proposalIdDecoder: Decoder[ProposalId] =
    Decoder.decodeString.map(ProposalId(_))
}

case class AuthorInfo(userId: UserId, firstName: Option[String], postalCode: Option[String], age: Option[Int])

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

  implicit lazy val proposalStatusEncoder: Encoder[ProposalStatus] = (gender: ProposalStatus) =>
    Json.fromString(gender.shortName)
  implicit lazy val proposalStatusDecoder: Decoder[ProposalStatus] =
    Decoder.decodeString.emap { value: String =>
      statusMap.get(value) match {
        case Some(profile) => Right(profile)
        case None          => Left(s"$value is not a proposal status")
      }
    }

  case object Pending extends ProposalStatus { override val shortName = "Pending" }

  case object Accepted extends ProposalStatus { override val shortName = "Accepted" }

  case object Refused extends ProposalStatus { override val shortName = "Refused" }

  case object Archived extends ProposalStatus { override val shortName = "Archived" }

}

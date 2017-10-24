package org.make.core.sequence

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder, Json}
import org.make.core.proposal.ProposalId
import org.make.core.reference.{TagId, ThemeId}
import org.make.core.user.UserId
import org.make.core.{MakeSerializable, RequestContext, StringValue, Timestamped}
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}
import spray.json.DefaultJsonProtocol._
import org.make.core.SprayJsonFormatters._

final case class SequenceTranslation(slug: String, title: String, language: String) extends MakeSerializable
object SequenceTranslation {
  implicit val sequenceTranslationFormatter: RootJsonFormat[SequenceTranslation] =
    DefaultJsonProtocol.jsonFormat3(SequenceTranslation.apply)

}
final case class SequenceAction(date: ZonedDateTime, user: UserId, actionType: String, arguments: Map[String, String])
object SequenceAction {
  implicit val sequenceActionFormatter: RootJsonFormat[SequenceAction] =
    DefaultJsonProtocol.jsonFormat4(SequenceAction.apply)
}

case class Sequence(sequenceId: SequenceId,
                    title: String,
                    slug: String,
                    tagIds: Seq[TagId] = Seq.empty,
                    proposalIds: Seq[ProposalId] = Seq.empty,
                    themeIds: Seq[ThemeId],
                    override val createdAt: Option[ZonedDateTime],
                    override val updatedAt: Option[ZonedDateTime],
                    status: SequenceStatus = SequenceStatus.Unpublished,
                    creationContext: RequestContext,
                    sequenceTranslation: Seq[SequenceTranslation] = Seq.empty,
                    events: List[SequenceAction],
                    searchable: Boolean)
    extends MakeSerializable
    with Timestamped

object Sequence {
  implicit val sequenceFormatter: RootJsonFormat[Sequence] =
    DefaultJsonProtocol.jsonFormat13(Sequence.apply)
}

final case class SequenceId(value: String) extends StringValue

object SequenceId {
  implicit lazy val sequenceIdEncoder: Encoder[SequenceId] =
    (a: SequenceId) => Json.fromString(a.value)
  implicit lazy val sequenceIdDecoder: Decoder[SequenceId] =
    Decoder.decodeString.map(SequenceId(_))

  implicit val sequenceIdFormatter: JsonFormat[SequenceId] = new JsonFormat[SequenceId] {
    override def read(json: JsValue): SequenceId = json match {
      case JsString(s) => SequenceId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: SequenceId): JsValue = {
      JsString(obj.value)
    }
  }
}

sealed trait SequenceStatus {
  def shortName: String
}
object SequenceStatus {
  val statusMap: Map[String, SequenceStatus] =
    Map(Unpublished.shortName -> Unpublished, Published.shortName -> Published)

  implicit lazy val sequenceStatusEncoder: Encoder[SequenceStatus] = (status: SequenceStatus) =>
    Json.fromString(status.shortName)

  implicit lazy val sequenceStatusDecoder: Decoder[SequenceStatus] =
    Decoder.decodeString.emap { value: String =>
      statusMap.get(value) match {
        case Some(sequence) => Right(sequence)
        case None           => Left(s"$value is not a sequence status")
      }
    }

  implicit val sequenceStatusFormatted: JsonFormat[SequenceStatus] = new JsonFormat[SequenceStatus] {
    override def read(json: JsValue): SequenceStatus = json match {
      case JsString(s) => SequenceStatus.statusMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: SequenceStatus): JsValue = {
      JsString(obj.shortName)
    }
  }

  case object Unpublished extends SequenceStatus {
    override val shortName = "Unpublished"
  }
  case object Published extends SequenceStatus {
    override val shortName = "Published"
  }
}

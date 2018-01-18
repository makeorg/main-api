package org.make.core.idea

import java.time.ZonedDateTime

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._
import org.make.core.CirceFormatters
import org.make.core.SprayJsonFormatters._
import org.make.core.operation.OperationId
import org.make.core.{MakeSerializable, StringValue, Timestamped}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

final case class Idea(ideaId: IdeaId,
                      name: String,
                      language: Option[String] = None,
                      country: Option[String] = None,
                      question: Option[String] = None,
                      operationId: Option[OperationId] = None,
                      status: IdeaStatus = IdeaStatus.Activated,
                      override val createdAt: Option[ZonedDateTime],
                      override val updatedAt: Option[ZonedDateTime])
    extends MakeSerializable
    with Timestamped

object Idea extends CirceFormatters {

  implicit val ideaFormatter: RootJsonFormat[Idea] =
    DefaultJsonProtocol.jsonFormat9(Idea.apply)

    implicit val encoder: Encoder[Idea] = deriveEncoder[Idea]
    implicit val decoder: Decoder[Idea] = deriveDecoder[Idea]
}

final case class IdeaId(value: String) extends StringValue

object IdeaId {
  implicit lazy val ideaIdEncoder: Encoder[IdeaId] =
    (a: IdeaId) => Json.fromString(a.value)
  implicit lazy val ideaIdDecoder: Decoder[IdeaId] =
    Decoder.decodeString.map(IdeaId(_))

  implicit val ideaIdFormatter: JsonFormat[IdeaId] = new JsonFormat[IdeaId] {
    override def read(json: JsValue): IdeaId = json match {
      case JsString(value) => IdeaId(value)
      case other           => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: IdeaId): JsValue = {
      JsString(obj.value)
    }
  }
}

sealed trait IdeaStatus {
  def shortName: String
}

object IdeaStatus {
  val statusMap: Map[String, IdeaStatus] =
    Map(
      Activated.shortName -> Activated,
      Archived.shortName -> Archived
    )

  implicit lazy val ideaStatusEncoder: Encoder[IdeaStatus] = (status: IdeaStatus) =>
    Json.fromString(status.shortName)
  implicit lazy val ideaStatusDecoder: Decoder[IdeaStatus] =
    Decoder.decodeString.emap { value: String =>
      statusMap.get(value) match {
        case Some(status) => Right(status)
        case None         => Left(s"$value is not an idea status")
      }
    }

  implicit val ideaStatusFormatted: JsonFormat[IdeaStatus] = new JsonFormat[IdeaStatus] {
    override def read(json: JsValue): IdeaStatus = json match {
      case JsString(s) => IdeaStatus.statusMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: IdeaStatus): JsValue = {
      JsString(obj.shortName)
    }
  }

  case object Archived extends IdeaStatus {
    override val shortName = "Archived"
  }

  case object Activated extends IdeaStatus {
    override val shortName = "Activated"
  }
}


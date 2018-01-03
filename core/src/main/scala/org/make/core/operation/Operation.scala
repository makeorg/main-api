package org.make.core.operation

import java.time.ZonedDateTime

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import org.make.core.SprayJsonFormatters._
import org.make.core.reference.TagId
import org.make.core.sequence.SequenceId
import org.make.core.user.UserId
import org.make.core.{DateHelper, MakeSerializable, StringValue, Timestamped}
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.annotation.meta.field

final case class Operation(status: OperationStatus,
                           operationId: OperationId,
                           slug: String,
                           translations: Seq[OperationTranslation] = Seq.empty,
                           defaultLanguage: String,
                           sequenceLandingId: SequenceId,
                           events: List[OperationAction],
                           override val createdAt: Option[ZonedDateTime],
                           override val updatedAt: Option[ZonedDateTime],
                           countriesConfiguration: Seq[OperationCountryConfiguration])
    extends MakeSerializable
    with Timestamped

object Operation {
  implicit val operationFormatter: RootJsonFormat[Operation] =
    DefaultJsonProtocol.jsonFormat10(Operation.apply)
}

final case class OperationId(value: String) extends StringValue

object OperationId {
  implicit lazy val operationIdEncoder: Encoder[OperationId] =
    (a: OperationId) => Json.fromString(a.value)
  implicit lazy val operationIdDecoder: Decoder[OperationId] =
    Decoder.decodeString.map(OperationId(_))

  implicit val operationIdFormatter: JsonFormat[OperationId] = new JsonFormat[OperationId] {
    override def read(json: JsValue): OperationId = json match {
      case JsString(s) => OperationId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: OperationId): JsValue = {
      JsString(obj.value)
    }
  }
}
@ApiModel
final case class OperationCountryConfiguration(countryCode: String,
                                               @(ApiModelProperty @field)(dataType = "list[string]") tagIds: Seq[TagId])

object OperationCountryConfiguration {
  implicit val operationCountryConfigurationFormatter: RootJsonFormat[OperationCountryConfiguration] =
    DefaultJsonProtocol.jsonFormat2(OperationCountryConfiguration.apply)

  implicit val encoder: ObjectEncoder[OperationCountryConfiguration] = deriveEncoder[OperationCountryConfiguration]
  implicit val decoder: Decoder[OperationCountryConfiguration] = deriveDecoder[OperationCountryConfiguration]
}

final case class OperationTranslation(title: String, language: String) extends MakeSerializable

object OperationTranslation {
  implicit val operationTranslationFormatter: RootJsonFormat[OperationTranslation] =
    DefaultJsonProtocol.jsonFormat2(OperationTranslation.apply)

  implicit val encoder: ObjectEncoder[OperationTranslation] = deriveEncoder[OperationTranslation]
  implicit val decoder: Decoder[OperationTranslation] = deriveDecoder[OperationTranslation]
}

final case class OperationAction(date: ZonedDateTime = DateHelper.now(),
                                 makeUserId: UserId,
                                 actionType: String,
                                 arguments: Map[String, String] = Map.empty)

object OperationAction {
  implicit val operationActionFormatter: RootJsonFormat[OperationAction] =
    DefaultJsonProtocol.jsonFormat4(OperationAction.apply)
}
sealed trait OperationActionType { val name: String }
case object OperationCreateAction extends OperationActionType { override val name: String = "create" }
case object OperationUpdateAction extends OperationActionType { override val name: String = "update" }
case object OperationActivateAction extends OperationActionType { override val name: String = "activate" }
case object OperationArchiveAction extends OperationActionType { override val name: String = "archive" }

sealed trait OperationStatus {
  def shortName: String
}
object OperationStatus {
  val statusMap: Map[String, OperationStatus] =
    Map(Pending.shortName -> Pending, Active.shortName -> Active, Archived.shortName -> Archived)

  implicit lazy val operationStatusEncoder: Encoder[OperationStatus] = (status: OperationStatus) =>
    Json.fromString(status.shortName)

  implicit lazy val operationStatusDecoder: Decoder[OperationStatus] =
    Decoder.decodeString.emap { value: String =>
      statusMap.get(value) match {
        case Some(operation) => Right(operation)
        case None            => Left(s"$value is not a operation status")
      }
    }

  implicit val operationStatusFormatted: JsonFormat[OperationStatus] = new JsonFormat[OperationStatus] {
    override def read(json: JsValue): OperationStatus = json match {
      case JsString(s) => OperationStatus.statusMap(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: OperationStatus): JsValue = {
      JsString(obj.shortName)
    }
  }

  case object Pending extends OperationStatus {
    override val shortName = "Pending"
  }
  case object Active extends OperationStatus {
    override val shortName = "Active"
  }
  case object Archived extends OperationStatus {
    override val shortName = "Archived"
  }
}

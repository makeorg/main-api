package org.make.api.technical

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import org.make.core.RequestContext
import org.make.core.proposal.indexed.{Qualification, QualificationKey, Vote, VoteKey}
import org.make.core.proposal.{ProposalId, ProposalStatus}
import org.make.core.reference.{LabelId, TagId, ThemeId}
import org.make.core.user.UserId
import spray.json.DefaultJsonProtocol._
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat}

trait SprayJsonFormatters {

  implicit val localDateFormatter: JsonFormat[LocalDate] = new JsonFormat[LocalDate] {
    override def read(json: JsValue): LocalDate = json match {
      case JsString(s) => LocalDate.parse(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: LocalDate): JsValue = {
      JsString(obj.toString)
    }
  }

  implicit val zonedDateTimeFormatter: JsonFormat[ZonedDateTime] = new JsonFormat[ZonedDateTime] {
    override def read(json: JsValue): ZonedDateTime = json match {
      case JsString(s) => ZonedDateTime.parse(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ZonedDateTime): JsValue = {
      JsString(obj.toString)
    }
  }

  implicit val uuidFormatter: JsonFormat[UUID] = new JsonFormat[UUID] {
    override def read(json: JsValue): UUID = json match {
      case JsString(s) => UUID.fromString(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: UUID): JsValue = {
      JsString(obj.toString)
    }
  }

  implicit val userIdFormatter: JsonFormat[UserId] = new JsonFormat[UserId] {
    override def read(json: JsValue): UserId = json match {
      case JsString(s) => UserId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: UserId): JsValue = {
      JsString(obj.value)
    }
  }

  implicit val proposalIdFormatter: JsonFormat[ProposalId] = new JsonFormat[ProposalId] {
    override def read(json: JsValue): ProposalId = json match {
      case JsString(s) => ProposalId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ProposalId): JsValue = {
      JsString(obj.value)
    }
  }

  implicit val themeIdFormatter: JsonFormat[ThemeId] = new JsonFormat[ThemeId] {
    override def read(json: JsValue): ThemeId = json match {
      case JsString(s) => ThemeId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ThemeId): JsValue = {
      JsString(obj.value)
    }
  }

  implicit val tagIdFormatter: JsonFormat[TagId] = new JsonFormat[TagId] {
    override def read(json: JsValue): TagId = json match {
      case JsString(s) => TagId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: TagId): JsValue = {
      JsString(obj.value)
    }
  }

  implicit val labelIdFormatter: JsonFormat[LabelId] = new JsonFormat[LabelId] {
    override def read(json: JsValue): LabelId = json match {
      case JsString(s) => LabelId(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: LabelId): JsValue = {
      JsString(obj.value)
    }
  }

  implicit val proposalStatusFormatter: JsonFormat[ProposalStatus] = new JsonFormat[ProposalStatus] {
    override def read(json: JsValue): ProposalStatus = json match {
      case JsString(s) =>
        ProposalStatus.statusMap.getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
      case other => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: ProposalStatus): JsValue = {
      JsString(obj.shortName)
    }
  }

  implicit val voteKeyFormatter: JsonFormat[VoteKey] = new JsonFormat[VoteKey] {
    override def read(json: JsValue): VoteKey = json match {
      case JsString(s) => VoteKey.voteKeys.getOrElse(s, throw new IllegalArgumentException(s"Unable to convert $s"))
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: VoteKey): JsValue = {
      JsString(obj.shortName)
    }
  }

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

  implicit val requestContextFormatter: RootJsonFormat[RequestContext] =
    DefaultJsonProtocol.jsonFormat10(RequestContext.apply)

  implicit val voteFormatter: RootJsonFormat[Vote] =
    DefaultJsonProtocol.jsonFormat5(Vote.apply)

  implicit val qualificationFormatter: RootJsonFormat[Qualification] =
    DefaultJsonProtocol.jsonFormat2(Qualification.apply)

}

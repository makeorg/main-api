package org.make.core

import java.time.{LocalDate, ZonedDateTime}
import java.util.UUID

import org.elasticsearch.search.sort.SortOrder
import spray.json.{JsString, JsValue, JsonFormat}

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

  implicit val sortOrderFormatted: JsonFormat[SortOrder] = new JsonFormat[SortOrder] {
    override def read(json: JsValue): SortOrder = json match {
      case JsString(s) => SortOrder.valueOf(s)
      case other       => throw new IllegalArgumentException(s"Unable to convert $other")
    }

    override def write(obj: SortOrder): JsValue = {
      JsString(obj.name())
    }
  }

}

object SprayJsonFormatters extends SprayJsonFormatters
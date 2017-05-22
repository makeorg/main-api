package org.make.api.technical

import java.time.{LocalDate, ZonedDateTime}

import com.sksamuel.avro4s._
import org.apache.avro.Schema
import org.apache.avro.Schema.Field

trait AvroSerializers {

  implicit object LocalDateToSchema extends ToSchema[LocalDate] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object DateTimeToValue extends ToValue[LocalDate] {
    override def apply(value: LocalDate): String = value.toString
  }

  implicit object DateTimeFromValue extends FromValue[LocalDate] {
    override def apply(value: Any, field: Field): LocalDate = LocalDate.parse(value.toString)
  }

  implicit object ZonedDateTimeToSchema extends ToSchema[ZonedDateTime] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object ZonedDateTimeToValue extends ToValue[ZonedDateTime] {
    override def apply(value: ZonedDateTime): String = value.toString
  }

  implicit object ZonedDateTimeFromValue extends FromValue[ZonedDateTime] {
    override def apply(value: Any, field: Field): ZonedDateTime = ZonedDateTime.parse(value.toString)
  }

}

package org.make.api.technical

import java.time.{LocalDate, ZonedDateTime}

import com.sksamuel.avro4s._
import org.apache.avro.Schema
import org.apache.avro.Schema.Field
import org.make.core.proposal.{QualificationKey, VoteKey}
import org.make.core.sequence.SequenceStatus

trait AvroSerializers {

  implicit object LocalDateToSchema extends ToSchema[LocalDate] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object DateTimeToValue extends ToValue[LocalDate] {
    override def apply(value: LocalDate): String = value.toString
  }

  implicit object DateTimeFromValue extends FromValue[LocalDate] {
    override def apply(value: Any, field: Field): LocalDate =
      LocalDate.parse(value.toString)
  }

  implicit object ZonedDateTimeToSchema extends ToSchema[ZonedDateTime] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object ZonedDateTimeToValue extends ToValue[ZonedDateTime] {
    override def apply(value: ZonedDateTime): String = value.toString
  }

  implicit object ZonedDateTimeFromValue extends FromValue[ZonedDateTime] {
    override def apply(value: Any, field: Field): ZonedDateTime =
      ZonedDateTime.parse(value.toString)
  }

  implicit object VoteKeyToSchema extends ToSchema[VoteKey] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object VoteKeyToValue extends ToValue[VoteKey] {
    override def apply(value: VoteKey): String = value.shortName
  }

  implicit object VoteKeyFromValue extends FromValue[VoteKey] {
    override def apply(value: Any, field: Field): VoteKey =
      VoteKey.matchVoteKey(value.toString).getOrElse(throw new IllegalArgumentException(s"$value is not a VoteKey"))
  }

  implicit object QualificationKeyToSchema extends ToSchema[QualificationKey] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object QualificationKeyToValue extends ToValue[QualificationKey] {
    override def apply(value: QualificationKey): String = value.shortName
  }

  implicit object QualificationKeyFromValue extends FromValue[QualificationKey] {
    override def apply(value: Any, field: Field): QualificationKey =
      QualificationKey
        .matchQualificationKey(value.toString)
        .getOrElse(throw new IllegalArgumentException(s"$value is not a QualificationKey"))
  }

  implicit object SequenceStatusToSchema extends ToSchema[SequenceStatus] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object SequenceStatusToValue extends ToValue[SequenceStatus] {
    override def apply(value: SequenceStatus): String = value.shortName
  }

  implicit object SequenceStatusFromValue extends FromValue[SequenceStatus] {
    override def apply(value: Any, field: Field): SequenceStatus =
      SequenceStatus.statusMap
        .get(value.toString)
        .getOrElse(throw new IllegalArgumentException(s"$value is not a SequenceStatus"))
  }

}

object AvroSerializers extends AvroSerializers

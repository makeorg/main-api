/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.core

import java.time.{LocalDate, ZonedDateTime}

import com.sksamuel.avro4s._
import org.apache.avro.Schema
import org.make.core.history.HistoryActions.{Trusted, VoteTrust}
import org.make.core.profile.{Gender, SocioProfessionalCategory}
import org.make.core.proposal.{QualificationKey, VoteKey}
import org.make.core.reference.{Country, Language}
import org.make.core.sequence.SequenceStatus

trait AvroSerializers {

  implicit object LocalDateSchemaFor extends SchemaFor[LocalDate] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object DateTimeEncoder extends Encoder[LocalDate] {
    override def encode(value: LocalDate, schema: Schema, fieldMapper: FieldMapper): String = value.toString
  }

  implicit object DateTimeDecoder extends Decoder[LocalDate] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): LocalDate =
      LocalDate.parse(value.toString)
  }

  implicit object ZonedDateTimeSchemaFor extends SchemaFor[ZonedDateTime] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object ZonedDateTimeEncoder extends Encoder[ZonedDateTime] {
    override def encode(value: ZonedDateTime, schema: Schema, fieldMapper: FieldMapper): String =
      DateHelper.format(value)
  }

  implicit object ZonedDateTimeDecoder extends Decoder[ZonedDateTime] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): ZonedDateTime =
      ZonedDateTime.parse(value.toString)
  }

  implicit object CountrySchemaFor extends SchemaFor[Country] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object CountryEncoder extends Encoder[Country] {
    override def encode(value: Country, schema: Schema, fieldMapper: FieldMapper): String = value.value
  }

  implicit object CountryDecoder extends Decoder[Country] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): Country = Country(value.toString)
  }

  implicit object LanguageSchemaFor extends SchemaFor[Language] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object LanguageEncoder extends Encoder[Language] {
    override def encode(value: Language, schema: Schema, fieldMapper: FieldMapper): String = value.value
  }

  implicit object LanguageDecoder extends Decoder[Language] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): Language = Language(value.toString)
  }

  implicit object GenderSchemaFor extends SchemaFor[Gender] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object GenderEncoder extends Encoder[Gender] {
    override def encode(value: Gender, schema: Schema, fieldMapper: FieldMapper): String = value.shortName
  }

  implicit object GenderDecoder extends Decoder[Gender] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): Gender =
      Gender.matchGender(value.toString).getOrElse(throw new IllegalArgumentException(s"$value is not a Gender"))
  }

  implicit object SocioProfessionalCategorySchemaFor extends SchemaFor[SocioProfessionalCategory] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object SocioProfessionalCategoryEncoder extends Encoder[SocioProfessionalCategory] {
    override def encode(value: SocioProfessionalCategory, schema: Schema, fieldMapper: FieldMapper): String =
      value.shortName
  }

  implicit object SocioProfessionalCategoryDecoder extends Decoder[SocioProfessionalCategory] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): SocioProfessionalCategory =
      SocioProfessionalCategory
        .matchSocioProfessionalCategory(value.toString)
        .getOrElse(throw new IllegalArgumentException(s"$value is not a SocioProfessionalCategory"))
  }

  implicit object VoteKeySchemaFor extends SchemaFor[VoteKey] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object VoteKeyEncoder extends Encoder[VoteKey] {
    override def encode(value: VoteKey, schema: Schema, fieldMapper: FieldMapper): String = value.shortName
  }

  implicit object VoteKeyDecoder extends Decoder[VoteKey] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): VoteKey =
      VoteKey.matchVoteKey(value.toString).getOrElse(throw new IllegalArgumentException(s"$value is not a VoteKey"))
  }

  implicit object QualificationKeySchemaFor extends SchemaFor[QualificationKey] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object QualificationKeyEncoder extends Encoder[QualificationKey] {
    override def encode(value: QualificationKey, schema: Schema, fieldMapper: FieldMapper): String = value.shortName
  }

  implicit object QualificationKeyDecoder extends Decoder[QualificationKey] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): QualificationKey =
      QualificationKey
        .matchQualificationKey(value.toString)
        .getOrElse(throw new IllegalArgumentException(s"$value is not a QualificationKey"))
  }

  implicit object SequenceStatusSchemaFor extends SchemaFor[SequenceStatus] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object SequenceStatusEncoder extends Encoder[SequenceStatus] {
    override def encode(value: SequenceStatus, schema: Schema, fieldMapper: FieldMapper): String = value.shortName
  }

  implicit object SequenceStatusDecoder extends Decoder[SequenceStatus] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): SequenceStatus =
      SequenceStatus.statusMap
        .getOrElse(value.toString, throw new IllegalArgumentException(s"$value is not a SequenceStatus"))
  }

  implicit object ApplicationNameSchemaFor extends SchemaFor[ApplicationName] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object ApplicationNameEncoder extends Encoder[ApplicationName] {
    override def encode(value: ApplicationName, schema: Schema, fieldMapper: FieldMapper): String = value.shortName
  }

  implicit object ApplicationNameDecoder extends Decoder[ApplicationName] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): ApplicationName =
      ApplicationName.applicationMap
        .getOrElse(value.toString, throw new IllegalArgumentException(s"$value is not an application name"))
  }

  implicit object VoteTrustSchemaFor extends SchemaFor[VoteTrust] {
    override def schema(fieldMapper: FieldMapper): Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object VoteTrustEncoder extends Encoder[VoteTrust] {
    override def encode(value: VoteTrust, schema: Schema, fieldMapper: FieldMapper): String = value.shortName
  }

  implicit object VoteTrustDecoder extends Decoder[VoteTrust] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): VoteTrust = {
      Option(value)
        .map(
          v =>
            VoteTrust.trustValue
              .getOrElse(v.toString, throw new IllegalArgumentException(s"$value is not a vote trust"))
        )
        .getOrElse(Trusted)
      // Add a fallback to trusted since some events in kafka are corrupted

    }
  }

}

object AvroSerializers extends AvroSerializers

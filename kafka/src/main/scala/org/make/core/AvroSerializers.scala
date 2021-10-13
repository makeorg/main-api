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
import enumeratum.values.{StringEnum, StringEnumEntry}
import org.apache.avro.Schema
import org.make.api.technical.crm.SendEmail
import org.make.core.history.HistoryActions.VoteTrust
import org.make.core.history.HistoryActions.VoteTrust.Trusted
import org.make.core.proposal.ProposalStatus
import org.make.core.reference.{Country, Language}

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

  // do not use automatic StringEnum typeclasses, to keep legacy complex schema
  implicit val proposalStatusSchemaFor: SchemaFor[ProposalStatus] = SchemaFor.gen[ProposalStatus]
  implicit val proposalStatusDecoder: Decoder[ProposalStatus] = Decoder.gen
  implicit val proposalStatusEncoder: Encoder[ProposalStatus] = Encoder.gen

  implicit val voteTrustDecoder: Decoder[VoteTrust] = new Decoder[VoteTrust] {
    override def decode(value: Any, schema: Schema, fieldMapper: FieldMapper): VoteTrust = {
      // Add a fallback to trusted since some events in kafka are corrupted
      Option(value).map(stringEnumDecoder[VoteTrust].decode(_, schema, fieldMapper)).getOrElse(Trusted)
    }
  }

  implicit def stringEnumSchemaFor[A <: StringEnumEntry]: SchemaFor[A] = _ => Schema.create(Schema.Type.STRING)

  implicit def stringEnumEncoder[A <: StringEnumEntry]: Encoder[A] = (a, _, _) => a.value

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  implicit def stringEnumDecoder[A <: StringEnumEntry](implicit enum: StringEnum[A]): Decoder[A] =
    (value, _, _) =>
      enum
        .withValueOpt(value.toString)
        .getOrElse(throw new IllegalArgumentException(s"$value is not a ${enum.toString}"))

  implicit val requestContextSchemaFor: SchemaFor[RequestContext] = SchemaFor.gen[RequestContext]
  implicit val requestContextAvroDecoder: Decoder[RequestContext] = Decoder.gen[RequestContext]
  implicit val requestContextAvroEncoder: Encoder[RequestContext] = Encoder.gen[RequestContext]

  implicit val sendEmailSchemaFor: SchemaFor[SendEmail] = SchemaFor.gen[SendEmail]
  implicit val sendEmailAvroDecoder: Decoder[SendEmail] = Decoder.gen[SendEmail]
  implicit val sendEmailAvroEncoder: Encoder[SendEmail] = Encoder.gen[SendEmail]

}

object AvroSerializers extends AvroSerializers

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

package org.make.api.technical

import java.time.{LocalDate, ZonedDateTime}

import com.sksamuel.avro4s._
import org.apache.avro.Schema
import org.apache.avro.Schema.Field
import org.make.api.technical.crm.MailJetError
import org.make.core.ApplicationName
import org.make.core.profile.{Gender, SocioProfessionalCategory}
import org.make.core.proposal.{QualificationKey, VoteKey}
import org.make.core.reference.{Country, Language}
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

  implicit object CountryToSchema extends ToSchema[Country] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object CountryToValue extends ToValue[Country] {
    override def apply(value: Country): String = value.value
  }

  implicit object CountryFromValue extends FromValue[Country] {
    override def apply(value: Any, field: Field): Country = Country(value.toString)
  }

  implicit object LanguageToSchema extends ToSchema[Language] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object LanguageToValue extends ToValue[Language] {
    override def apply(value: Language): String = value.value
  }

  implicit object LanguageFromValue extends FromValue[Language] {
    override def apply(value: Any, field: Field): Language = Language(value.toString)
  }

  implicit object GenderToSchema extends ToSchema[Gender] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object GenderToValue extends ToValue[Gender] {
    override def apply(value: Gender): String = value.shortName
  }

  implicit object GenderFromValue extends FromValue[Gender] {
    override def apply(value: Any, field: Field): Gender =
      Gender.matchGender(value.toString).getOrElse(throw new IllegalArgumentException(s"$value is not a Gender"))
  }

  implicit object SocioProfessionalCategoryToSchema extends ToSchema[SocioProfessionalCategory] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object SocioProfessionalCategoryToValue extends ToValue[SocioProfessionalCategory] {
    override def apply(value: SocioProfessionalCategory): String = value.shortName
  }

  implicit object SocioProfessionalCategoryFromValue extends FromValue[SocioProfessionalCategory] {
    override def apply(value: Any, field: Field): SocioProfessionalCategory =
      SocioProfessionalCategory
        .matchSocioProfessionalCategory(value.toString)
        .getOrElse(throw new IllegalArgumentException(s"$value is not a SocioProfessionalCategory"))
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
        .getOrElse(value.toString, throw new IllegalArgumentException(s"$value is not a SequenceStatus"))
  }

  implicit object MailJetErrorToValue extends ToValue[MailJetError] {
    override def apply(value: MailJetError): String = value.name
  }

  implicit object MailJetErrorFromValue extends FromValue[MailJetError] {
    override def apply(value: Any, field: Field): MailJetError =
      MailJetError.errorMap
        .getOrElse(value.toString, throw new IllegalArgumentException(s"$value is not a MailJetError"))
  }

  implicit object MailJetErrorToSchema extends ToSchema[MailJetError] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object ApplicationNameToSchema extends ToSchema[ApplicationName] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit object ApplicationNameToValue extends ToValue[ApplicationName] {
    override def apply(value: ApplicationName): String = value.shortName
  }

  implicit object ApplicationNameFromValue extends FromValue[ApplicationName] {
    override def apply(value: Any, field: Field): ApplicationName =
      ApplicationName.applicationMap
        .getOrElse(value.toString, throw new IllegalArgumentException(s"$value is not an application name"))
  }

}

object AvroSerializers extends AvroSerializers

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

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import enumeratum.values.{StringEnum, StringEnumEntry}
import enumeratum.{Enum, EnumEntry}
import org.make.core.demographics.DemographicsCardId
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.personality.PersonalityRoleId
import org.make.core.proposal.{ProposalId, ProposalKeywordKey}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, LabelId, Language}
import org.make.core.tag.{TagId, TagTypeId}
import org.make.core.user.UserId

import scala.concurrent.{ExecutionContext, Future}
import org.make.core.technical.Pagination._
import org.make.core.widget.SourceId

@SuppressWarnings(Array("org.wartremover.warts.Throw"))
trait ParameterExtractors {

  implicit def eitherNoneOrT[T](
    implicit unmarshaller: Unmarshaller[String, T]
  ): Unmarshaller[String, Either[None.type, T]] = {

    Unmarshaller.identityUnmarshaller.transform {
      implicit executionContext: ExecutionContext => implicit materializer: Materializer =>
        _.flatMap {
          case "None" => Future.successful(Left(None))
          case other  => unmarshaller(other).map(Right(_))
        }
    }
  }

  implicit val proposalKeywordKeyFromStringUnmarshaller: Unmarshaller[String, ProposalKeywordKey] =
    Unmarshaller.strict[String, ProposalKeywordKey](ProposalKeywordKey.apply)

  implicit val localDateFromStringUnmarshaller: Unmarshaller[String, LocalDate] =
    Unmarshaller.strict[String, LocalDate] { string =>
      LocalDate.parse(string)
    }

  implicit val zonedDateTimeFromStringUnmarshaller: Unmarshaller[String, ZonedDateTime] =
    Unmarshaller.strict[String, ZonedDateTime] {
      case value if value.contains('T') => ZonedDateTime.parse(value)
      case value                        => LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault())
    }

  implicit val languageFromStringUnmarshaller: Unmarshaller[String, Language] =
    Unmarshaller.strict[String, Language] { string =>
      Language(string.toLowerCase())
    }

  implicit val countryFromStringUnmarshaller: Unmarshaller[String, Country] =
    Unmarshaller.strict[String, Country] { string =>
      Country(string.toUpperCase())
    }

  implicit val proposalIdFromStringUnmarshaller: Unmarshaller[String, ProposalId] =
    Unmarshaller.strict[String, ProposalId] { string =>
      ProposalId(string)
    }

  implicit val userIdFromStringUnmarshaller: Unmarshaller[String, UserId] =
    Unmarshaller.strict[String, UserId] { string =>
      UserId(string)
    }

  implicit val labelIdFromStringUnmarshaller: Unmarshaller[String, LabelId] =
    Unmarshaller.strict[String, LabelId] { string =>
      LabelId(string)
    }

  implicit val tagIdFromStringUnmarshaller: Unmarshaller[String, TagId] =
    Unmarshaller.strict[String, TagId] { string =>
      TagId(string)
    }

  implicit val tagTypeIdFromStringUnmarshaller: Unmarshaller[String, TagTypeId] =
    Unmarshaller.strict[String, TagTypeId] { string =>
      TagTypeId(string)
    }

  implicit val operationIdFromStringUnmarshaller: Unmarshaller[String, OperationId] =
    Unmarshaller.strict[String, OperationId] { string =>
      OperationId(string)
    }

  implicit val questionIdFromStringUnmarshaller: Unmarshaller[String, QuestionId] =
    Unmarshaller.strict[String, QuestionId] { string =>
      QuestionId(string)
    }

  implicit val sourceIdFromStringUnmarshaller: Unmarshaller[String, SourceId] =
    Unmarshaller.strict[String, SourceId] { string =>
      SourceId(string)
    }

  implicit val ideaIdFromStringUnmarshaller: Unmarshaller[String, IdeaId] =
    Unmarshaller.strict[String, IdeaId] { string =>
      IdeaId(string)
    }

  implicit val personalityRoleIdFromStringUnmarshaller: Unmarshaller[String, PersonalityRoleId] =
    Unmarshaller.strict[String, PersonalityRoleId] { role =>
      PersonalityRoleId(role)
    }

  implicit val demographicsCardIdFromStringUnmarshaller: Unmarshaller[String, DemographicsCardId] =
    Unmarshaller.strict[String, DemographicsCardId] { role =>
      DemographicsCardId(role)
    }

  implicit def enumeratumEnumUnmarshaller[A <: EnumEntry](implicit basicEnum: Enum[A]): Unmarshaller[String, A] =
    Unmarshaller.strict(basicEnum.withNameInsensitiveEither(_).fold(e => throw new Exception(e), identity))

  implicit def enumeratumStringEnumUnmarshaller[A <: StringEnumEntry](
    implicit stringEnum: StringEnum[A]
  ): Unmarshaller[String, A] =
    Unmarshaller.strict(s => stringEnum.withValueEither(s).fold(e => throw new Exception(e), identity))

  implicit val startFromIntUnmarshaller: Unmarshaller[String, Start] =
    Unmarshaller.intFromStringUnmarshaller.map(Start.apply)
  implicit val endFromIntUnmarshaller: Unmarshaller[String, End] = Unmarshaller.intFromStringUnmarshaller.map(End.apply)
  implicit val limitFromIntUnmarshaller: Unmarshaller[String, Limit] =
    Unmarshaller.intFromStringUnmarshaller.map(Limit.apply)

}

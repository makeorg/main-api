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
import com.sksamuel.elastic4s.searches.sort.SortOrder
import com.sksamuel.elastic4s.searches.sort.SortOrder.{Asc, Desc}
import org.make.core.idea.IdeaId
import org.make.core.operation.{OperationId, OperationKind}
import org.make.core.partner.PartnerKind
import org.make.core.personality.{FieldType, PersonalityRoleId}
import org.make.core.proposal.{ProposalId, ProposalStatus, QualificationKey, VoteKey}
import org.make.core.question.{QuestionId, TopProposalsMode}
import org.make.core.reference.{Country, LabelId, Language}
import org.make.core.tag.{TagId, TagTypeId}
import org.make.core.user.{UserId, UserType}

import scala.concurrent.{ExecutionContext, Future}

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

  implicit val operationKindStringUnmarshaller: Unmarshaller[String, OperationKind] =
    Unmarshaller.strict[String, OperationKind] { string =>
      OperationKind.kindMap.getOrElse(
        string,
        throw ValidationFailedError(
          Seq(ValidationError("operationKind", "invalid_value", Some(s"$string is not a valid operation kind")))
        )
      )
    }

  implicit val questionIdFromStringUnmarshaller: Unmarshaller[String, QuestionId] =
    Unmarshaller.strict[String, QuestionId] { string =>
      QuestionId(string)
    }

  implicit val ideaIdFromStringUnmarshaller: Unmarshaller[String, IdeaId] =
    Unmarshaller.strict[String, IdeaId] { string =>
      IdeaId(string)
    }

  implicit val proposalStatusFromStringUnmarshaller: Unmarshaller[String, ProposalStatus] =
    Unmarshaller.strict[String, ProposalStatus] { string =>
      ProposalStatus.statusMap.getOrElse(
        string,
        throw ValidationFailedError(
          Seq(ValidationError("status", "invalid_value", Some(s"$string is not a valid proposal status")))
        )
      )
    }

  implicit val voteKeyFromStringUnmarshaller: Unmarshaller[String, VoteKey] =
    Unmarshaller.strict[String, VoteKey] { string =>
      VoteKey.voteKeys.getOrElse(
        string,
        throw ValidationFailedError(
          Seq(ValidationError("vote", "invalid_value", Some(s"$string is not a valid vote key")))
        )
      )
    }

  implicit val qualificationKeyFromStringUnmarshaller: Unmarshaller[String, QualificationKey] =
    Unmarshaller.strict[String, QualificationKey] { string =>
      QualificationKey.qualificationKeys.getOrElse(
        string,
        throw ValidationFailedError(
          Seq(ValidationError("qualification", "invalid_value", Some(s"$string is not a valid qualification key")))
        )
      )
    }

  implicit val sortOrderFromStringUnmarshaller: Unmarshaller[String, SortOrder] =
    Unmarshaller.strict[String, SortOrder] {
      case value if value.toLowerCase == "asc"  => Asc
      case value if value.toLowerCase == "desc" => Desc
      case string =>
        throw ValidationFailedError(
          Seq(ValidationError("order", "invalid_value", Some(s"$string is not a valid sort order")))
        )
    }

  implicit val partnerKindFromStringUnmarshaller: Unmarshaller[String, PartnerKind] =
    Unmarshaller.strict[String, PartnerKind] { string =>
      PartnerKind.kindMap.getOrElse(
        string,
        throw ValidationFailedError(
          Seq(ValidationError("partnerKind", "invalid_value", Some(s"$string is not a valid partner kind")))
        )
      )
    }

  implicit val userTypeFromStringUnmarshaller: Unmarshaller[String, UserType] = {
    Unmarshaller.strict[String, UserType] { value =>
      UserType.userTypes.getOrElse(
        value,
        throw ValidationFailedError(
          Seq(ValidationError("userType", "invalid_value", Some(s"$value is not a valid user type")))
        )
      )
    }
  }

  implicit val topProposalsModeFromStringUnmarshaller: Unmarshaller[String, TopProposalsMode] = {
    Unmarshaller.strict[String, TopProposalsMode] { value =>
      TopProposalsMode.modes.getOrElse(
        value,
        throw ValidationFailedError(
          Seq(ValidationError("topProposalsMode", "invalid_value", Some(s"$value is not a valid mode")))
        )
      )
    }
  }

  implicit val personalityRoleIdFromStringUnmarshaller: Unmarshaller[String, PersonalityRoleId] =
    Unmarshaller.strict[String, PersonalityRoleId] { role =>
      PersonalityRoleId(role)
    }

  implicit val fieldTypeFromStringUnmarshaller: Unmarshaller[String, FieldType] =
    Unmarshaller.strict[String, FieldType] { fieldType =>
      FieldType
        .matchFieldType(fieldType)
        .getOrElse(
          throw ValidationFailedError(
            Seq(ValidationError("fieldType", "invalid_value", Some(s"$fieldType is not a valid field type")))
          )
        )
    }

}

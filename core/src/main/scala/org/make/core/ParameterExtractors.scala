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

import java.time.LocalDate

import akka.http.scaladsl.unmarshalling.Unmarshaller
import org.make.core.idea.IdeaId
import org.make.core.operation.OperationId
import org.make.core.proposal.{ProposalId, ProposalStatus, QualificationKey, VoteKey}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, LabelId, Language, ThemeId}
import org.make.core.tag.{TagId, TagTypeId}

trait ParameterExtractors {

  implicit val localDateFromStringUnmarshaller: Unmarshaller[String, LocalDate] =
    Unmarshaller.strict[String, LocalDate] { string ⇒
      LocalDate.parse(string)
    }

  implicit val languageFromStringUnmarshaller: Unmarshaller[String, Language] =
    Unmarshaller.strict[String, Language] { string ⇒
      Language(string.toLowerCase())
    }

  implicit val countryFromStringUnmarshaller: Unmarshaller[String, Country] =
    Unmarshaller.strict[String, Country] { string ⇒
      Country(string.toUpperCase())
    }

  implicit val proposalIdFromStringUnmarshaller: Unmarshaller[String, ProposalId] =
    Unmarshaller.strict[String, ProposalId] { string ⇒
      ProposalId(string)
    }

  implicit val themeIdFromStringUnmarshaller: Unmarshaller[String, ThemeId] =
    Unmarshaller.strict[String, ThemeId] { string ⇒
      ThemeId(string)
    }

  implicit val labelIdFromStringUnmarshaller: Unmarshaller[String, LabelId] =
    Unmarshaller.strict[String, LabelId] { string ⇒
      LabelId(string)
    }

  implicit val tagIdFromStringUnmarshaller: Unmarshaller[String, TagId] =
    Unmarshaller.strict[String, TagId] { string ⇒
      TagId(string)
    }

  implicit val tagTypeIdFromStringUnmarshaller: Unmarshaller[String, TagTypeId] =
    Unmarshaller.strict[String, TagTypeId] { string ⇒
      TagTypeId(string)
    }

  implicit val operationIdFromStringUnmarshaller: Unmarshaller[String, OperationId] =
    Unmarshaller.strict[String, OperationId] { string ⇒
      OperationId(string)
    }

  implicit val questionIdFromStringUnmarshaller: Unmarshaller[String, QuestionId] =
    Unmarshaller.strict[String, QuestionId] { string ⇒
      QuestionId(string)
    }

  implicit val ideaIdFromStringUnmarshaller: Unmarshaller[String, IdeaId] =
    Unmarshaller.strict[String, IdeaId] { string ⇒
      IdeaId(string)
    }

  implicit val proposalStatusFromStringUnmarshaller: Unmarshaller[String, ProposalStatus] =
    Unmarshaller.strict[String, ProposalStatus] { string ⇒
      ProposalStatus.statusMap.getOrElse(
        string,
        throw ValidationFailedError(Seq(ValidationError("status", Some(s"$string is not a valid proposal status"))))
      )
    }

  implicit val voteKeyFromStringUnmarshaller: Unmarshaller[String, VoteKey] =
    Unmarshaller.strict[String, VoteKey] { string ⇒
      VoteKey.voteKeys.getOrElse(
        string,
        throw ValidationFailedError(Seq(ValidationError("vote", Some(s"$string is not a valid vote key"))))
      )
    }

  implicit val qualificationKeyFromStringUnmarshaller: Unmarshaller[String, QualificationKey] =
    Unmarshaller.strict[String, QualificationKey] { string ⇒
      QualificationKey.qualificationKeys.getOrElse(
        string,
        throw ValidationFailedError(
          Seq(ValidationError("qualification", Some(s"$string is not a valid qualification key")))
        )
      )
    }

}

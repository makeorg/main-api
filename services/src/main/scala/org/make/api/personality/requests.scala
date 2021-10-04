/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.personality

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.ApiModelProperty
import org.make.core.Validation.{validate, validateUserInput}
import org.make.core.personality.{FieldType, PersonalityRoleId}
import org.make.core.question.QuestionId
import org.make.core.user.UserId

import scala.annotation.meta.field

final case class CreatePersonalityRoleFieldRequest(
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "STRING", allowableValues = "INT,STRING,BOOLEAN")
  fieldType: FieldType,
  required: Boolean
) {
  validate(validateUserInput("name", name, None))
}

object CreatePersonalityRoleFieldRequest {
  implicit val decoder: Decoder[CreatePersonalityRoleFieldRequest] = deriveDecoder[CreatePersonalityRoleFieldRequest]
}

final case class CreatePersonalityRoleRequest(name: String) {
  validate(validateUserInput("name", name, None))
}

object CreatePersonalityRoleRequest {
  implicit val decoder: Decoder[CreatePersonalityRoleRequest] = deriveDecoder[CreatePersonalityRoleRequest]
}

final case class CreateQuestionPersonalityRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "6a90575f-f625-4025-a485-8769e8a26967")
  questionId: QuestionId,
  @(ApiModelProperty @field)(dataType = "string", example = "0c3cbbf4-42c1-4801-b08a-d0e60d136041")
  personalityRoleId: PersonalityRoleId
)

object CreateQuestionPersonalityRequest {
  implicit val decoder: Decoder[CreateQuestionPersonalityRequest] = deriveDecoder[CreateQuestionPersonalityRequest]
}

final case class UpdatePersonalityRoleFieldRequest(
  name: String,
  @(ApiModelProperty @field)(dataType = "string", example = "STRING", allowableValues = "INT,STRING,BOOLEAN")
  fieldType: FieldType,
  required: Boolean
) {
  validate(validateUserInput("name", name, None))
}

object UpdatePersonalityRoleFieldRequest {
  implicit val decoder: Decoder[UpdatePersonalityRoleFieldRequest] = deriveDecoder[UpdatePersonalityRoleFieldRequest]
}

final case class UpdatePersonalityRoleRequest(name: String) {
  validate(validateUserInput("name", name, None))
}

object UpdatePersonalityRoleRequest {
  implicit val decoder: Decoder[UpdatePersonalityRoleRequest] = deriveDecoder[UpdatePersonalityRoleRequest]
}

final case class UpdateQuestionPersonalityRequest(
  @(ApiModelProperty @field)(dataType = "string", example = "e4be2934-64a5-4c58-a0a8-481471b4ff2e")
  userId: UserId,
  @(ApiModelProperty @field)(dataType = "string", example = "0c3cbbf4-42c1-4801-b08a-d0e60d136041")
  personalityRoleId: PersonalityRoleId
)

object UpdateQuestionPersonalityRequest {
  implicit val decoder: Decoder[UpdateQuestionPersonalityRequest] = deriveDecoder[UpdateQuestionPersonalityRequest]
}

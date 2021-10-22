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

package org.make.api.question

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.swagger.annotations.ApiModelProperty
import org.make.core.DateHelper
import org.make.core.Validation.{
  validate,
  validateAge,
  validateField,
  validateOptional,
  validateOptionalUserInput,
  validatePostalCode,
  validateUserInput
}

import scala.annotation.meta.field

final case class AuthorRequest(
  @(ApiModelProperty @field)(dataType = "integer", example = "23", allowableValues = "range[8, 120)")
  age: Option[Int],
  firstName: String,
  lastName: Option[String],
  @(ApiModelProperty @field)(dataType = "string", example = "12345")
  postalCode: Option[String],
  profession: Option[String]
) {
  validate(
    validateAge("age", age.map(DateHelper.computeBirthDate)),
    validateUserInput("firstName", firstName, None),
    validateField("firstName", "mandatory", firstName.nonEmpty, "firstName should not be empty"),
    validateOptionalUserInput("lastName", lastName, None),
    validateOptionalUserInput("postalCode", postalCode, None),
    validateOptionalUserInput("profession", profession, None)
  )
  validateOptional(postalCode.map(value => validatePostalCode("postalCode", value, None)))
}

object AuthorRequest {
  implicit val decoder: Decoder[AuthorRequest] = deriveDecoder[AuthorRequest]
}

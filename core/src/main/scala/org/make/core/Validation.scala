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

import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.jsoup.Jsoup
import org.jsoup.safety.{Cleaner, Whitelist}

import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object Validation extends StrictLogging {

  // Taken from https://emailregex.com/ a more simple one might be needed
  val emailRegex: Regex =
    ("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21" +
      "\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+" +
      "[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
      "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a" +
      "\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])").r

  val colorRegex: Regex = "^#[0-9a-fA-F]{6}$".r

  val postalCodeRegex: Regex = "^\\d{5}$".r

  def validateOptional(maybeRequire: Option[Requirement]*): Unit = validate(maybeRequire.flatten: _*)

  def validate(require: Requirement*): Unit = {
    val messages: Seq[ValidationError] = require.flatMap { requirement =>
      Try(requirement.condition()) match {
        case Failure(e) =>
          Seq(ValidationError(requirement.field, requirement.key, Option(e.getMessage)))
        case Success(false) =>
          Seq(ValidationError(requirement.field, requirement.key, Option(requirement.message())))
        case _ => Nil
      }
    }
    if (messages.nonEmpty) {
      throw ValidationFailedError(messages)
    }
  }

  def validateField(field: String, key: String, condition: => Boolean, message: => String): Requirement =
    Requirement(field, key, () => condition, () => message)

  def maxLength(field: String,
                maxLength: Int,
                fieldValue: String,
                message: Option[Int => String] = None): Requirement = {

    val computeLength: Int = {
      Option(fieldValue).map(_.length).getOrElse(0)
    }
    val isValid = {
      computeLength <= maxLength
    }

    Requirement(field, "too_long", () => isValid, () => {
      if (isValid) {
        ""
      } else {
        message.map(_(computeLength)).getOrElse(s"$field should not be longer than $maxLength")
      }
    })

  }

  def minLength(field: String,
                minLength: Int,
                fieldValue: String,
                message: Option[Int => String] = None): Requirement = {

    val computeLength: Int = {
      Option(fieldValue).map(_.length).getOrElse(0)
    }
    val isValid = {
      computeLength >= minLength
    }

    Requirement(field, "too_short", () => isValid, () => {
      if (isValid) {
        ""
      } else {
        message.map(_(computeLength)).getOrElse(s"$field should not be shorter than $minLength")
      }
    })

  }

  def mandatoryField(fieldName: String, fieldValue: => Any, message: Option[String] = None): Requirement = {
    val condition: () => Boolean = () => {
      val value = fieldValue
      exists(value) && value != None
    }
    validateField(fieldName, "mandatory", condition(), message.getOrElse(s"$fieldName is mandatory"))
  }

  def validateEmail(fieldName: String, fieldValue: => String, message: Option[String] = None): Requirement = {
    val condition: () => Boolean = () => {
      val value: String = fieldValue
      val maybeEmail = emailRegex.findFirstIn(value)
      exists(value) && maybeEmail.isDefined && maybeEmail.contains(value)
    }
    validateField(fieldName, "invalid_email", condition(), message.getOrElse(s"$fieldName is not a valid email"))
  }

  def requireNonEmpty(fieldName: String, fieldValue: => Seq[_], message: Option[String] = None): Requirement = {
    validateField(fieldName, "mandatory", fieldValue.nonEmpty, message.getOrElse(s"$fieldName should not be empty"))
  }

  def requirePresent(fieldName: String, fieldValue: => Option[_], message: Option[String] = None): Requirement = {
    validateField(fieldName, "mandatory", fieldValue.nonEmpty, message.getOrElse(s"$fieldName should not be empty"))
  }

  def validateUserInput(fieldName: String, fieldValue: => String, message: Option[String]): Requirement = {
    val condition: () => Boolean = () => {
      new Cleaner(Whitelist.none()).isValid(Jsoup.parse(fieldValue))
    }
    validateField(fieldName, "invalid_content", condition(), message.getOrElse(s"$fieldName is not a valid user input"))
  }

  def validateOptionalUserInput(fieldName: String,
                                fieldValue: => Option[String],
                                message: Option[String]): Requirement = {
    val condition: () => Boolean = () => {
      fieldValue.forall(value => new Cleaner(Whitelist.none()).isValid(Jsoup.parse(value)))
    }
    validateField(fieldName, "invalid_content", condition(), message.getOrElse(s"$fieldName is not a valid user input"))
  }

  def requireValidSlug(fieldName: String,
                       fieldValue: => Option[String],
                       message: Option[String] = None): Requirement = {
    validateField(
      fieldName,
      "invalid_slug",
      fieldValue.getOrElse("") == SlugHelper.apply(fieldValue.getOrElse("")),
      message.getOrElse(s"$fieldName should not be empty")
    )
  }

  def requireEmpty(fieldName: String, fieldValue: => Seq[_], message: Option[String] = None): Requirement = {
    validateField(fieldName, "non_empty", fieldValue.isEmpty, message.getOrElse(s"$fieldName should be empty"))
  }

  def requireNotPresent(fieldName: String, fieldValue: => Option[_], message: Option[String] = None): Requirement = {
    validateField(fieldName, "non_empty", fieldValue.isEmpty, message.getOrElse(s"$fieldName should be empty"))
  }

  def validMatch(fieldName: String,
                 fieldValue: => String,
                 message: Option[String] = None,
                 regex: Regex): Requirement = {
    val condition: () => Boolean = () => {
      val value: String = fieldValue
      exists(value) && regex.findFirstIn(value).isDefined
    }
    validateField(fieldName, "invalid_content", condition(), message.getOrElse(s"$fieldName is not valid"))
  }

  def validChoices[T](fieldName: String,
                      message: Option[String] = None,
                      userChoices: Seq[T],
                      validChoices: Seq[T]): Requirement = {
    val condition: () => Boolean = () => {
      userChoices.forall(validChoices.contains)
    }
    validateField(fieldName, "invalid_value", condition(), message.getOrElse(s"$fieldName is not valid"))
  }

  def validateEquals(fieldName: String,
                     message: Option[String] = None,
                     userValue: Any,
                     expectedValue: Any): Requirement = {
    val condition: () => Boolean = () => {
      userValue.equals(expectedValue)
    }
    validateField(fieldName, "invalid_value", condition(), message.getOrElse(s"$fieldName is not valid"))
  }

  def validateEntity(fieldName: String,
                     message: Option[String] = None,
                     userValue: Option[MakeSerializable]): Requirement = {
    validateField(fieldName, "mandatory", userValue.nonEmpty, message.getOrElse(s"$fieldName does not exist"))
  }

  def validateAge(fieldName: String, userDateInput: Option[LocalDate], message: Option[String] = None): Requirement = {
    val condition: Boolean = userDateInput.forall(
      date =>
        LocalDate.now().minusYears(120).isBefore(date) &&
          LocalDate.now().minusYears(13).plusDays(1).isAfter(date)
    )
    validateField(
      fieldName,
      "invalid_age",
      condition,
      message.getOrElse("Invalid date: age must be between 13 and 120")
    )
  }

  def validateColor(fieldName: String, userColorInput: => String, message: Option[String]): Requirement = {
    val condition = colorRegex.findFirstIn(userColorInput).isDefined
    validateField(
      fieldName,
      "invalid_color",
      condition,
      message.getOrElse("Invalid color. Must be formatted '#123456'")
    )
  }

  def validatePostalCode(fieldName: String, userPostalCodeInput: => String, message: Option[String]): Requirement = {
    val condition = userPostalCodeInput.isEmpty || postalCodeRegex.findFirstIn(userPostalCodeInput).isDefined
    validateField(
      fieldName,
      "invalid_postal_code",
      condition,
      message.getOrElse("Invalid postal code. Must be formatted '01234'")
    )
  }

  private def exists(value: Any): Boolean = {
    Option(value).isDefined
  }

}

case class Requirement(field: String, key: String, condition: () => Boolean, message: () => String)

case class ValidationFailedError(errors: Seq[ValidationError]) extends Exception {
  override def getMessage: String = { errors.asJson.toString }
}

case class ValidationError(field: String, key: String, message: Option[String])

object ValidationError {
  implicit val encoder: Encoder[ValidationError] = deriveEncoder[ValidationError]
  implicit val decoder: Decoder[ValidationError] = deriveDecoder[ValidationError]
}

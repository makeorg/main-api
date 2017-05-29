package org.make.core

import io.circe.generic.auto._
import io.circe.syntax._

import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

object Validation {

  // Taken from https://emailregex.com/ a more simple one might be needed
  val emailRegex: Regex =
    "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])".r

  def validate(require: Requirement*): Unit = {
    val messages: Seq[ValidationError] = require.flatMap { requirement =>
      Try(requirement.condition()) match {
        case Failure(e) =>
          Seq(ValidationError(requirement.field, e.getMessage))
        case Success(false) =>
          Seq(ValidationError(requirement.field, requirement.message()))
        case _ => Nil
      }
    }
    if (messages.nonEmpty) {
      throw ValidationFailedError(messages)
    }
  }

  def validateField(field: String, condition: => Boolean, message: => String): Requirement =
    Requirement(field, () => condition, () => message)

  def mandatoryField(fieldName: String, fieldValue: => Any, message: Option[String] = None): Requirement = {
    val condition: () => Boolean = () => {
      val value = fieldValue
      exists(value) && value != None
    }
    validateField(fieldName, condition(), message.getOrElse(s"$fieldName is mandatory"))
  }

  def validateEmail(fieldName: String, fieldValue: => String, message: Option[String] = None): Requirement = {
    val condition: () => Boolean = () => {
      val value: String = fieldValue
      exists(value) && emailRegex.findFirstIn(value).isDefined
    }
    validateField(fieldName, condition(), message.getOrElse(s"$fieldName is not a valid email"))
  }

  private def exists(value: Any): Boolean = {
    Option(value).isDefined
  }

}

case class Requirement(field: String, condition: () => Boolean, message: () => String)

case class ValidationFailedError(errors: Seq[ValidationError]) extends Exception {
  override def getMessage: String = { errors.asJson.toString }
}

case class ValidationError(field: String, message: String)

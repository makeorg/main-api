package org.make.core

import scala.util.{Failure, Success, Try}
import io.circe.syntax._

object Validation {

  def requirements(require: Requirement*): Unit = {
    val messages: Seq[String] = require.flatMap { requirement =>
      Try(requirement.condition()) match {
        case Failure(e) => Seq(e.getMessage)
        case Success(false) => Seq(requirement.message())
        case _ => Nil
      }
    }
    if(messages.nonEmpty) {
      throw ValidationError(messages)
    }
  }

  def requirement(condition: => Boolean, message: => String): Requirement = Requirement(() => condition, () => message)

}

case class Requirement(condition: () => Boolean, message: () => String)

case class ValidationError(errors: Seq[String]) extends Exception {
  override def getMessage: String = { errors.asJson.toString}
}

package org.make.core.user

import java.time.LocalDate

import io.circe._
import org.make.core.{MakeSerializable, StringValue}

//
// file containing classes and services of the user domain
//

case class User(userId: UserId, email: String, dateOfBirth: LocalDate, firstName: String, lastName: String)
    extends MakeSerializable

case class UserId(value: String) extends StringValue

object UserId {
  implicit lazy val userIdEncoder: Encoder[UserId] = (a: UserId) => Json.fromString(a.value)
  implicit lazy val userIdDecoder: Decoder[UserId] =
    Decoder.decodeString.map(UserId(_))
}

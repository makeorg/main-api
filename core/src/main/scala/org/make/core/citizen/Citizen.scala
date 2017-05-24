package org.make.core.citizen

import java.time.LocalDate

import io.circe._
import org.make.core.Validation._
import org.make.core.StringValue

//
// file containing classes and services of the citizen domain
//

case class Citizen(
                    citizenId: CitizenId,
                    email: String,
                    dateOfBirth: LocalDate,
                    firstName: String,
                    lastName: String
                  )


case class CitizenId(value: String) extends StringValue

object CitizenId {
  implicit lazy val citizenIdEncoder: Encoder[CitizenId] = (a: CitizenId) => Json.fromString(a.value)
  implicit lazy val citizenIdDecoder: Decoder[CitizenId] = Decoder.decodeString.map(CitizenId(_))
}


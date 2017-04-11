package org.make.core.citizen

import java.time.LocalDate

trait StringValue {
  def value: String
}

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




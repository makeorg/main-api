package org.make.core.citizen

import java.time.LocalDate

sealed trait CitizenCommand {
  def citizenId: CitizenId
}

case class RegisterCommand(citizenId: CitizenId,
                           email: String,
                           dateOfBirth: LocalDate,
                           firstName: String,
                           lastName: String) extends CitizenCommand

case class UpdateProfileCommand(citizenId: CitizenId) extends CitizenCommand

case class GetCitizen(citizenId: CitizenId) extends CitizenCommand


package org.make.core.citizen

import java.time.LocalDate


sealed trait CitizenEvent {
  def citizenId: CitizenId
}

case class CitizenRegistered(
                              citizenId: CitizenId,
                              email: String,
                              dateOfBirth: LocalDate,
                              firstName: String,
                              lastName: String
                            ) extends CitizenEvent

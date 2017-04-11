package org.make.api.citizen

import java.time.LocalDate

import io.finch._
import org.make.core.citizen.{Citizen, CitizenId}

object CitizenApi {

  def citizenId: Endpoint[CitizenId] = string.map(CitizenId)

  def getCitizen: Endpoint[Citizen] = get("citizen" :: citizenId) { citizenId: CitizenId =>
    Ok(Citizen(
      citizenId = citizenId,
      email = "test@make.org",
      dateOfBirth = LocalDate.now(),
      firstName = "firstName",
      lastName = "lastName"
    ))
  }

}

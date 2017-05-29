package org.make.api.citizen

import java.time.LocalDate

import org.make.api.technical.IdGeneratorComponent
import org.make.core.citizen.{Citizen, CitizenId}

import scala.concurrent.Future

trait CitizenServiceComponent { this: IdGeneratorComponent with PersistentCitizenServiceComponent =>

  def citizenService: CitizenService

  class CitizenService {

    def getCitizen(id: CitizenId): Future[Option[Citizen]] = {
      persistentCitizenService.get(id)
    }

    def register(email: String,
                 dateOfBirth: LocalDate,
                 firstName: String,
                 lastName: String,
                 password: String): Future[Citizen] = {

      persistentCitizenService.persist(
        Citizen(
          citizenId = idGenerator.nextCitizenId(),
          dateOfBirth = dateOfBirth,
          email = email,
          firstName = firstName,
          lastName = lastName
        ),
        password
      )
    }

  }

  /*class CitizenService(actor: ActorRef) {

    implicit private val defaultTimeout = Timeout(2.seconds)

    def getCitizen(citizenId: CitizenId): Future[Option[Citizen]] = {
      (actor ? GetCitizen(citizenId)).mapTo[Option[Citizen]]
    }

    def register(email: String,
                 dateOfBirth: LocalDate,
                 firstName: String,
                 lastName: String): Future[Option[Citizen]] = {
      (
        actor ?
          RegisterCommand(
            citizenId = idGenerator.nextCitizenId(),
            email = email,
            dateOfBirth = dateOfBirth,
            firstName = firstName,
            lastName = lastName
          )
        ).mapTo[Option[Citizen]]
    }

  }*/

}

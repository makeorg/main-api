package org.make.api.citizen

import java.time.LocalDate

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import org.make.api.IdGeneratorComponent
import org.make.core.citizen.{Citizen, CitizenId, GetCitizen, RegisterCommand}

import scala.concurrent.Future
import scala.concurrent.duration._

trait CitizenServiceComponent {
  this: IdGeneratorComponent =>

  def citizenService: CitizenService


  class CitizenService(actor: ActorRef) {

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

  }

}

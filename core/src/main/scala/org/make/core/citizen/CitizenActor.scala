package org.make.core.citizen

import java.time.LocalDate

import akka.persistence.PersistentActor

class CitizenActor(citizenId: CitizenId) extends PersistentActor {

  var state: CitizenState = CitizenState(citizenId)

  override def receiveRecover: Receive = {
    case e: CitizenEvent => applyEvent(e)
  }

  override def receiveCommand: Receive = {
    case register: RegisterCommand => persist(CitizenRegistered(citizenId))(applyEvent)
    case updateProfile: UpdateProfileCommand =>
    case GetCitizen => sender() ! state.toCitizen
  }

  override def persistenceId: String = citizenId.value


  private def applyEvent(event: CitizenEvent): Unit = {

  }

  case class CitizenState(
                           citizenId: CitizenId,
                           email: Option[String] = None,
                           dateOfBirth: Option[LocalDate] = None,
                           firstName: Option[String] = None,
                           lastName: Option[String] = None

                         ) {

    def toCitizen: Citizen = {
      Citizen(
        citizenId = this.citizenId,
        email = this.email.orNull,
        dateOfBirth = this.dateOfBirth.orNull,
        firstName = this.firstName.orNull,
        lastName = this.lastName.orNull
      )
    }
  }

}

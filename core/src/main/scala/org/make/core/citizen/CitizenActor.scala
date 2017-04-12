package org.make.core.citizen

import java.time.LocalDate

import akka.actor.Props
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.pattern.{Patterns, ask}
import org.make.core.citizen.CitizenActor.Snapshot

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.duration._

class CitizenActor extends PersistentActor {

  def citizenId = CitizenId(self.path.name)

  private[this] var state: Option[CitizenState] = None

  override def receiveRecover: Receive = {
    case e: CitizenEvent =>
      println(s"Recovering event $e")
      applyEvent(e)
    case SnapshotOffer(_, snapshot) =>
      println(s"Recovering from snapshot $snapshot")
      val citizen = snapshot.asInstanceOf[Citizen]
      state = Some(CitizenState(
        citizenId = citizenId,
        email = Option(citizen.email),
        dateOfBirth = Option(citizen.dateOfBirth),
        firstName = Option(citizen.firstName),
        lastName = Option(citizen.lastName)
    ))
    case _: RecoveryCompleted =>
  }

  override def receiveCommand: Receive = {
    case register: RegisterCommand => persist(
      CitizenRegistered(
        citizenId = citizenId,
        email = register.email,
        dateOfBirth = register.dateOfBirth,
        firstName = register.firstName,
        lastName = register.lastName
      )
    )(applyEvent)
      Patterns.pipe((self ? GetCitizen(citizenId)) (1.second), Implicits.global).to(sender())
      self ! Snapshot

    case updateProfile: UpdateProfileCommand =>
    case GetCitizen(_) => sender() ! state.map(_.toCitizen)
    case Snapshot => state.foreach(state => saveSnapshot(state.toCitizen))
  }

  override def persistenceId: String = citizenId.value


  private val applyEvent: PartialFunction[CitizenEvent, Unit] = {
    case e: CitizenRegistered => state = Some(CitizenState(
      citizenId = citizenId,
      email = Option(e.email),
      dateOfBirth = Option(e.dateOfBirth),
      firstName = Option(e.firstName),
      lastName = Option(e.lastName)
    ))
    case _ =>
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

object CitizenActor {

  val props: Props = Props[CitizenActor]

  case object Snapshot

}
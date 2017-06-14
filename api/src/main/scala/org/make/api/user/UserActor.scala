package org.make.api.user

import java.time.LocalDate

import akka.actor.{ActorLogging, PoisonPill, Props}
import akka.pattern.{ask, Patterns}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import org.make.api.user.UserActor.Snapshot
import org.make.core.user.UserEvent.UserRegistered
import org.make.core.user.UserEvent
import org.make.core.user._

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.duration._

class UserActor extends PersistentActor with ActorLogging {

  def userId: UserId = UserId(self.path.name)

  private[this] var state: Option[UserState] = None

  override def receiveRecover: Receive = {
    case e: UserEvent =>
      log.info(s"Recovering event $e")
      applyEvent(e)
    case SnapshotOffer(_, snapshot) =>
      log.info(s"Recovering from snapshot $snapshot")
      val user = snapshot.asInstanceOf[User]
      state = Some(
        UserState(
          userId = userId,
          email = Option(user.email),
          dateOfBirth = user.profile.get.birthdate,
          firstName = Option(user.firstName),
          lastName = Option(user.lastName)
        )
      )
    case _: RecoveryCompleted =>
  }

  override def receiveCommand: Receive = {
    case register: RegisterCommand =>
      val events = UserRegistered(
        id = userId,
        email = register.email,
        dateOfBirth = register.dateOfBirth,
        firstName = register.firstName,
        lastName = register.lastName
      )

      persist(events)(applyEvent)

      context.system.eventStream.publish(events)

      Patterns
        .pipe((self ? GetUser(userId))(1.second), Implicits.global)
        .to(sender())
      self ! Snapshot

    case _: UpdateProfileCommand =>
    case GetUser(_)           => sender() ! state.map(_.toUser)
    case Snapshot                => state.foreach(state => saveSnapshot(state.toUser))
    case KillUserShard(_)     => self ! PoisonPill
  }

  override def persistenceId: String = userId.value

  private val applyEvent: PartialFunction[UserEvent, Unit] = {
    case e: UserRegistered =>
      state = Some(
        UserState(
          userId = userId,
          email = Option(e.email),
          dateOfBirth = Option(e.dateOfBirth),
          firstName = Option(e.firstName),
          lastName = Option(e.lastName)
        )
      )
    case _ =>
  }

  case class UserState(userId: UserId,
                       email: Option[String] = None,
                       dateOfBirth: Option[LocalDate] = None,
                       firstName: Option[String] = None,
                       lastName: Option[String] = None) {

    def toUser: User = {
      ???
      // TODO: implement new user model
      /**
      User(
        userId = this.userId,
        email = this.email.orNull,
        dateOfBirth = this.profile.birthdate.orNull,
        firstName = this.firstName.orNull,
        lastName = this.lastName.orNull
      )
      **/
    }
  }

}

object UserActor {

  val props: Props = Props[UserActor]

  case object Snapshot

}

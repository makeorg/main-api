package org.make.api.technical

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import org.make.api.technical.MakePersistentActor.Snapshot

import scala.collection.immutable
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

abstract class MakePersistentActor[State, Event <: AnyRef](stateClass: Class[State],
                                                           eventClass: Class[Event],
                                                           autoSnapshot: Boolean = true)
    extends PersistentActor
    with ActorLogging {

  protected val defaultTimeout: Timeout = Timeout(3.seconds)

  protected val snapshotThreshold = 10
  protected var eventsCount: Int = 0
  var state: Option[State] = None

  override def receiveRecover: Receive = {
    case e if eventClass.isAssignableFrom(e.getClass) =>
      eventsCount += 1
      Try(applyEvent(eventClass.cast(e))) match {
        case Success(newState)  => state = newState
        case Failure(exception) => log.error(exception, "Unable to apply event {}, ignoring it", e)
      }
    case SnapshotOffer(_, snapshot) if stateClass.isAssignableFrom(snapshot.getClass) =>
      eventsCount = 0
      state = Some(stateClass.cast(snapshot))
    case RecoveryCompleted =>
      if (autoSnapshot && eventsCount >= snapshotThreshold) {
        self ! Snapshot
      }
      onRecoveryCompleted()
    case _ =>
  }

  def onRecoveryCompleted(): Unit = {}

  def applyEvent: PartialFunction[Event, Option[State]]

  protected def saveSnapshot(): Unit = {
    state.foreach { s =>
      saveSnapshot(s)
      eventsCount = 0
    }
  }

  protected def newEventAdded(event: Event): Unit = {
    state = applyEvent(event)
    eventsCount += 1
    if (autoSnapshot && eventsCount >= snapshotThreshold) {
      self ! Snapshot
    }
  }

  protected def persistAndPublishEvent[T <: Event](event: T)(andThen: T => Unit): Unit = {
    persist(event) { event: T =>
      newEventAdded(event)
      context.system.eventStream.publish(event)
      andThen(event)
    }
  }

  protected def persistAndPublishEvents(events: immutable.Seq[Event])(andThen: Event => Unit): Unit = {
    persistAll(events) { event: Event =>
      newEventAdded(event)
      context.system.eventStream.publish(event)
      andThen(event)
    }
  }

}

object MakePersistentActor {

  case object Snapshot
}

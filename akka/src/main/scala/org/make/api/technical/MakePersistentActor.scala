/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.api.technical

import akka.actor.{ActorLogging, ClassicActorSystemProvider}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.persistence.cassandra.cleanup.{Cleanup, CleanupSettings}
import akka.util.Timeout
import com.typesafe.config.ConfigValueFactory
import org.make.api.technical.Futures._
import org.make.api.technical.MakePersistentActor.Snapshot
import org.make.core.StringValue

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

abstract class MakePersistentActor[State, Event <: AnyRef](
                                                            stateClass: Class[State],
                                                            eventClass: Class[Event],
                                                            autoSnapshot: Boolean = true
                                                          ) extends PersistentActor
  with ActorLogging {

  protected val defaultTimeout: Timeout = TimeSettings.defaultTimeout

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
    case other if unhandledRecover.isDefinedAt(other) => unhandledRecover(other)
    case _                                            =>
  }

  protected def unhandledRecover: Receive = {
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

}

object MakePersistentActor extends ShortenedNames {

  case object Snapshot extends ActorProtocol
  final case class StartShard(shardId: String) extends ActorProtocol

  def delete[ID <: StringValue](
    id: ID,
    plugin: String
  )(implicit ec: EC, provider: ClassicActorSystemProvider): Future[Unit] = {
    new Cleanup(
      provider,
      new CleanupSettings(
        provider.classicSystem.settings.config
          .getConfig("akka.persistence.cassandra.cleanup")
          .withValue("plugin-location", ConfigValueFactory.fromAnyRef(plugin.dropRight(".journal".length)))
      )
    ).deleteAll(persistenceId = id.value, neverUsePersistenceIdAgain = true).toUnit
  }
}

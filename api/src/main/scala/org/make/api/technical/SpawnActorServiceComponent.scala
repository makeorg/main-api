/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, Behavior, Props, SpawnProtocol}
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import org.make.api.ActorSystemTypedComponent

import scala.concurrent.Future

trait SpawnActorService {
  def spawn[T](behavior: Behavior[T], name: String, props: Props = Props.empty): Future[ActorRef[T]]
}

trait SpawnActorServiceComponent {
  def spawnActorService: SpawnActorService
}

trait SpawnActorRefComponent {
  def spawnActorRef: ActorRef[SpawnProtocol.Command]
}

trait DefaultSpawnActorServiceComponent extends SpawnActorServiceComponent with StrictLogging {
  this: ActorSystemTypedComponent with SpawnActorRefComponent =>

  override val spawnActorService: SpawnActorService = new DefaultSpawnActorService

  class DefaultSpawnActorService extends SpawnActorService {

    implicit val timeout: Timeout = TimeSettings.defaultTimeout

    override def spawn[T](behavior: Behavior[T], name: String, props: Props = Props.empty): Future[ActorRef[T]] =
      spawnActorRef ? { replyTo: ActorRef[ActorRef[T]] =>
        SpawnProtocol.Spawn(behavior = behavior, name = name, props = props, replyTo = replyTo)
      }

  }
}

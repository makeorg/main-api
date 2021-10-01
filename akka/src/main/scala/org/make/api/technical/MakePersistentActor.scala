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

import akka.actor.ClassicActorSystemProvider
import akka.persistence.cassandra.cleanup.{Cleanup, CleanupSettings}
import com.typesafe.config.ConfigValueFactory
import org.make.api.technical.Futures._
import org.make.core.StringValue

import scala.concurrent.Future

object MakePersistentActor extends ShortenedNames {
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

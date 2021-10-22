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

package org.make.api.extensions

import akka.actor.Actor
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.typesafe.config.Config
import org.make.api.ActorSystemTypedComponent

class MakeSettingsExtension(config: Config) extends MakeSettings(config) with Extension

object MakeSettingsExtension extends ExtensionId[MakeSettingsExtension] {
  override def createExtension(system: ActorSystem[_]): MakeSettingsExtension =
    new MakeSettingsExtension(system.settings.config.getConfig("make-api"))
}

trait ActorMakeSettingsComponent { self: Actor =>
  val settings: MakeSettings = MakeSettingsExtension(context.system.toTyped)
}

trait DefaultMakeSettingsComponent extends MakeSettingsComponent {
  self: ActorSystemTypedComponent =>

  override lazy val makeSettings: MakeSettings = MakeSettingsExtension(actorSystemTyped)
}

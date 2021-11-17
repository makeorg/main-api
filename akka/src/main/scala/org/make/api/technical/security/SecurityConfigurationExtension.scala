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

package org.make.api.technical.security

import akka.actor.typed.{ActorSystem, Extension, ExtensionId}
import com.typesafe.config.Config
import org.make.api.technical.ActorSystemComponent

class SecurityConfigurationExtension(config: Config) extends SecurityConfiguration(config) with Extension

object SecurityConfigurationExtension extends ExtensionId[SecurityConfigurationExtension] {
  override def createExtension(system: ActorSystem[_]): SecurityConfigurationExtension =
    new SecurityConfigurationExtension(system.settings.config.getConfig("make-api.security"))
}

trait DefaultSecurityConfigurationComponent extends SecurityConfigurationComponent {
  this: ActorSystemComponent =>
  override lazy val securityConfiguration: SecurityConfiguration = SecurityConfigurationExtension(actorSystem)
}

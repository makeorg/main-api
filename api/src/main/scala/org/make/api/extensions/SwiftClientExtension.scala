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

package org.make.api.extensions

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import org.make.swift.SwiftClient

class SwiftClientExtension(val swiftClient: SwiftClient) extends Extension

object SwiftClientExtension extends ExtensionId[SwiftClientExtension] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): SwiftClientExtension =
    new SwiftClientExtension(SwiftClient.create(system))

  override def lookup: ExtensionId[SwiftClientExtension] =
    SwiftClientExtension
}

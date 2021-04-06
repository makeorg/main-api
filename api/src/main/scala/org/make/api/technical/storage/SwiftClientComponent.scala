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

package org.make.api.technical.storage

import org.make.api.ActorSystemTypedComponent
import org.make.api.extensions.SwiftClientExtension
import org.make.swift.SwiftClient

trait SwiftClientComponent {
  def swiftClient: SwiftClient
}

trait DefaultSwiftClientComponent extends SwiftClientComponent { this: ActorSystemTypedComponent =>
  override lazy val swiftClient: SwiftClient = SwiftClientExtension(actorSystemTyped).swiftClient
}

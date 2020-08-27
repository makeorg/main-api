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

package org.make.api.technical.tracing

import kamon.Kamon
import kamon.context.Context

object Tracing {

  private val EntrypointKey: Context.Key[Option[String]] = Context.key(EntrypointPatternConverter.name, None)

  def entrypoint: Option[String] = Kamon.currentContext.get(EntrypointKey)

  def entrypoint(name: String): Unit = {
    Kamon.storeContext(Kamon.currentContext.withEntry(EntrypointKey, Some(name)))
    ()
  }

  def clearEntrypoint(): Unit = {
    Kamon.storeContext(Kamon.currentContext().withoutEntry(EntrypointKey))
    ()
  }

}

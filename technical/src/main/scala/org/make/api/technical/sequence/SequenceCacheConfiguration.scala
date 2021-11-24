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

package org.make.api.technical.sequence

import com.typesafe.config.Config
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.refineV
import eu.timepit.refined.types.numeric.PosInt

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

class SequenceCacheConfiguration(config: Config) {
  val inactivityTimeout: FiniteDuration =
    FiniteDuration(Duration(config.getString("inactivity-timeout")).toSeconds, TimeUnit.SECONDS)
  val checkInactivityTimer: FiniteDuration =
    FiniteDuration(Duration(config.getString("check-inactivity-timer")).toSeconds, TimeUnit.SECONDS)
  val proposalsPoolSize: PosInt = refineV[Positive].unsafeFrom(config.getInt("proposals-pool-size"))
  val cacheRefreshCycles: Int = config.getInt("cache-refresh-cycles")
}

trait SequenceCacheConfigurationComponent {
  def sequenceCacheConfiguration: SequenceCacheConfiguration
}

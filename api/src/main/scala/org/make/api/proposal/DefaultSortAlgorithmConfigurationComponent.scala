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

package org.make.api.proposal

import com.typesafe.config.Config
import org.make.api.ConfigComponent
import org.make.core.proposal.SortAlgorithmConfiguration

trait SortAlgorithmConfigurationComponent {
  def sortAlgorithmConfiguration: SortAlgorithmConfiguration
}

trait DefaultSortAlgorithmConfigurationComponent extends SortAlgorithmConfigurationComponent {
  this: ConfigComponent =>

  override def sortAlgorithmConfiguration: SortAlgorithmConfiguration =
    new DefaultSortAlgorithmConfiguration(config)

  class DefaultSortAlgorithmConfiguration(config: Config) extends SortAlgorithmConfiguration {
    override val controversyThreshold: Double = config.getDouble("make-api.proposal-algorithm.controversy-threshold")
    override val controversyVoteCountThreshold: Int =
      config.getInt("make-api.proposal-algorithm.controversy-vote-count-threshold")
    override val popularVoteCountThreshold: Int =
      config.getInt("make-api.proposal-algorithm.popular-vote-count-threshold")
    override val realisticThreshold: Double = config.getDouble("make-api.proposal-algorithm.realistic-threshold")
    override val realisticVoteCountThreshold: Int =
      config.getInt("make-api.proposal-algorithm.realistic-vote-count-threshold")
  }
}

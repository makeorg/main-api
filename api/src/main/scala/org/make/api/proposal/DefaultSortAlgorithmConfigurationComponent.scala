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
import org.make.api.ActorSystemComponent
import org.make.core.proposal.SortAlgorithmConfiguration

trait SortAlgorithmConfigurationComponent {
  def sortAlgorithmConfiguration: SortAlgorithmConfiguration
}

trait DefaultSortAlgorithmConfigurationComponent extends SortAlgorithmConfigurationComponent {
  this: ActorSystemComponent =>

  override def sortAlgorithmConfiguration: SortAlgorithmConfiguration =
    new DefaultSortAlgorithmConfiguration(actorSystem.settings.config)

  class DefaultSortAlgorithmConfiguration(config: Config) extends SortAlgorithmConfiguration {
    override val controversyTreshold: Double = config.getDouble("make-api.proposal-algorithm.controversy-treshold")
    override val popularVoteCountTreshold: Int =
      config.getInt("make-api.proposal-algorithm.popular-vote-count-treshold")
    override val realisticTreshold: Double = config.getDouble("make-api.proposal-algorithm.realistic-treshold")
  }
}

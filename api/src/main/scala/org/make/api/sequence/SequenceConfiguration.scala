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

package org.make.api.sequence

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import org.make.api.sequence.SequenceConfigurationActor.{
  GetPersistentSequenceConfiguration,
  GetSequenceConfiguration,
  SetSequenceConfiguration
}
import org.make.api.technical.TimeSettings
import org.make.core.sequence.SequenceId

import scala.concurrent.Future

case class SequenceConfiguration(sequenceId: SequenceId,
                                 maxAvailableProposals: Int = 1000,
                                 newProposalsRatio: Double = 0.5,
                                 newProposalsVoteThreshold: Int = 100,
                                 testedProposalsEngagementThreshold: Double = 0.0,
                                 testedProposalsScoreThreshold: Double = 0.0,
                                 testedProposalsControversyThreshold: Double = 0.0,
                                 testedProposalsMaxVotesThreshold: Int = 1500,
                                 banditEnabled: Boolean = false,
                                 banditMinCount: Int = 1,
                                 banditProposalsRatio: Double = 0.0,
                                 ideaCompetitionEnabled: Boolean = false,
                                 ideaCompetitionTargetCount: Int = 50,
                                 ideaCompetitionControversialRatio: Double = 0.0,
                                 ideaCompetitionControversialCount: Int = 0)

object SequenceConfiguration {
  implicit val decoder: Decoder[SequenceConfiguration] = deriveDecoder[SequenceConfiguration]
  implicit val encoder: Encoder[SequenceConfiguration] = deriveEncoder[SequenceConfiguration]
}

trait SequenceConfigurationService {
  def getSequenceConfiguration(sequenceId: SequenceId): Future[SequenceConfiguration]
  def setSequenceConfiguration(sequenceConfiguration: SequenceConfiguration): Future[Boolean]
  def getPersistentSequenceConfiguration(sequenceId: SequenceId): Future[Option[SequenceConfiguration]]
}

trait SequenceConfigurationComponent {
  val sequenceConfigurationService: SequenceConfigurationService
}

trait DefaultSequenceConfigurationComponent extends SequenceConfigurationComponent with StrictLogging {
  self: SequenceConfigurationActorComponent =>

  implicit val timeout: Timeout = TimeSettings.defaultTimeout

  override lazy val sequenceConfigurationService: SequenceConfigurationService = new SequenceConfigurationService {
    override def getSequenceConfiguration(sequenceId: SequenceId): Future[SequenceConfiguration] = {
      (sequenceConfigurationActor ? GetSequenceConfiguration(sequenceId)).mapTo[SequenceConfiguration]
    }

    override def setSequenceConfiguration(sequenceConfiguration: SequenceConfiguration): Future[Boolean] = {
      (sequenceConfigurationActor ? SetSequenceConfiguration(sequenceConfiguration)).mapTo[Boolean]
    }

    override def getPersistentSequenceConfiguration(sequenceId: SequenceId): Future[Option[SequenceConfiguration]] = {
      (sequenceConfigurationActor ? GetPersistentSequenceConfiguration(sequenceId)).mapTo[Option[SequenceConfiguration]]
    }
  }
}

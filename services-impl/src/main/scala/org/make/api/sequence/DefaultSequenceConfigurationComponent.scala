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

import akka.actor.typed.Scheduler
import akka.util.Timeout
import grizzled.slf4j.Logging
import org.make.api.sequence.SequenceConfigurationActor._
import org.make.api.technical.BetterLoggingActors._
import org.make.api.technical.{ActorSystemComponent, TimeSettings}
import org.make.core.question.QuestionId
import org.make.core.sequence.{SequenceConfiguration, SequenceId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DefaultSequenceConfigurationComponent extends SequenceConfigurationComponent with Logging {
  self: SequenceConfigurationActorComponent with PersistentSequenceConfigurationComponent with ActorSystemComponent =>

  override lazy val sequenceConfigurationService: SequenceConfigurationService = new DefaultSequenceConfigurationService

  class DefaultSequenceConfigurationService extends SequenceConfigurationService {
    implicit val timeout: Timeout = TimeSettings.defaultTimeout
    implicit val scheduler: Scheduler = actorSystem.scheduler

    override def getSequenceConfigurationByQuestionId(questionId: QuestionId): Future[SequenceConfiguration] = {
      (sequenceConfigurationActor ?? (GetSequenceConfiguration(questionId, _)))
        .map(_.sequenceConfiguration)
    }

    override def setSequenceConfiguration(sequenceConfiguration: SequenceConfiguration): Future[Boolean] = {
      persistentSequenceConfigurationService.persist(sequenceConfiguration)
    }

    override def getPersistentSequenceConfiguration(sequenceId: SequenceId): Future[Option[SequenceConfiguration]] = {
      persistentSequenceConfigurationService.findOne(sequenceId)
    }

    override def getPersistentSequenceConfigurationByQuestionId(
      questionId: QuestionId
    ): Future[Option[SequenceConfiguration]] = {
      persistentSequenceConfigurationService.findOne(questionId)
    }

    override def reloadConfigurations(): Unit = {
      sequenceConfigurationActor ! ReloadSequenceConfiguration
    }
  }
}

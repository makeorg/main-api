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

package org.make.api.migrations
import org.make.api.MakeApi
import org.make.api.sequence.SequenceConfiguration

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object CreateSequenceConfigurations extends Migration {
  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}
  override def migrate(api: MakeApi): Future[Unit] = {
    api.operationService.find(None, None, None, None).flatMap { operations =>
      sequentially(operations.flatMap(_.questions)) { question =>
        api.persistentSequenceConfigurationService.findOne(question.details.landingSequenceId).flatMap {
          case Some(_) => Future.successful {}
          case None =>
            api.persistentSequenceConfigurationService
              .persist(
                SequenceConfiguration(
                  sequenceId = question.details.landingSequenceId,
                  questionId = question.question.questionId
                )
              )
              .map(_ => ())
        }
      }
    }

  }
  override def runInProduction: Boolean = true
}

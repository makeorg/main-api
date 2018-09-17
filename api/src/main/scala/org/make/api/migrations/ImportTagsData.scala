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

import java.util.concurrent.Executors

import com.typesafe.scalalogging.StrictLogging
import org.make.api.MakeApi
import org.make.core.question.Question

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

trait ImportTagsData extends Migration with StrictLogging with TagHelper {

  var question: Question = _

  override def initialize(api: MakeApi): Future[Unit] = Future.successful {}

  implicit override val executor: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))

  def dataResource: String

  override def migrate(api: MakeApi): Future[Unit] = {
    importTags(api, dataResource, question)
  }
}

object ImportTagsData {}

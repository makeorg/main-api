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

package org.make.api.operation

import java.time.ZonedDateTime
import org.make.core.Order
import org.make.core.operation._
import org.make.core.question.QuestionId
import org.make.core.sequence.SequenceId

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentOperationOfQuestionService {
  def search(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    questionIds: Option[Seq[QuestionId]],
    operationIds: Option[Seq[OperationId]],
    operationKind: Option[Seq[OperationKind]],
    openAt: Option[ZonedDateTime],
    endAfter: Option[ZonedDateTime],
    slug: Option[String]
  ): Future[Seq[OperationOfQuestion]]
  def persist(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion]
  def modify(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion]
  def getById(id: QuestionId): Future[Option[OperationOfQuestion]]
  def find(operationId: Option[OperationId] = None): Future[Seq[OperationOfQuestion]]
  def delete(questionId: QuestionId): Future[Unit]
  def count(
    questionIds: Option[Seq[QuestionId]],
    operationIds: Option[Seq[OperationId]],
    openAt: Option[ZonedDateTime],
    endAfter: Option[ZonedDateTime],
    slug: Option[String]
  ): Future[Int]
  // TODO: delete this method once the calling batch was run in production
  def questionIdFromSequenceId(sequenceId: SequenceId): Future[Option[QuestionId]]
}

trait PersistentOperationOfQuestionServiceComponent {
  def persistentOperationOfQuestionService: PersistentOperationOfQuestionService
}

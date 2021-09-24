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

package org.make.api.question

import org.make.core.question.{Question, QuestionId}

import scala.concurrent.Future

trait PersistentQuestionServiceComponent {
  def persistentQuestionService: PersistentQuestionService
}

trait PersistentQuestionService {
  def count(request: SearchQuestionRequest): Future[Int]
  def find(request: SearchQuestionRequest): Future[Seq[Question]]
  def getById(questionId: QuestionId): Future[Option[Question]]
  def getByIds(questionIds: Seq[QuestionId]): Future[Seq[Question]]
  def getByQuestionIdValueOrSlug(questionIdValueOrSlug: String): Future[Option[Question]]
  def persist(question: Question): Future[Question]
  def modify(question: Question): Future[Question]
  def delete(question: QuestionId): Future[Unit]
}

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

package org.make.api.idea

import org.make.core.idea.{IdeaId, TopIdea, TopIdeaId}
import org.make.core.question.QuestionId
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentTopIdeaServiceComponent {
  def persistentTopIdeaService: PersistentTopIdeaService
}

trait PersistentTopIdeaService {
  def getById(topIdeaId: TopIdeaId): Future[Option[TopIdea]]
  def getByIdAndQuestionId(topIdeaId: TopIdeaId, questionId: QuestionId): Future[Option[TopIdea]]
  def search(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    ideaId: Option[IdeaId],
    questionIds: Option[Seq[QuestionId]],
    name: Option[String]
  ): Future[Seq[TopIdea]]
  def persist(topIdea: TopIdea): Future[TopIdea]
  def modify(topIdea: TopIdea): Future[TopIdea]
  def remove(topIdeaId: TopIdeaId): Future[Unit]
  def count(ideaId: Option[IdeaId], questionId: Option[QuestionId], name: Option[String]): Future[Int]
}

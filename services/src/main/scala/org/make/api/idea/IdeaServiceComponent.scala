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

import org.make.core.idea.indexed.IdeaSearchResult
import org.make.core.idea.{Idea, IdeaId, IdeaSearchQuery, IdeaStatus}
import org.make.core.question.{Question, QuestionId}

import scala.concurrent.Future

trait IdeaServiceComponent {
  def ideaService: IdeaService
}

trait IdeaService {
  def fetchAll(ideaSearchQuery: IdeaSearchQuery): Future[IdeaSearchResult]
  def fetchAllByIdeaIds(ids: Seq[IdeaId]): Future[Seq[Idea]]
  def fetchOne(ideaId: IdeaId): Future[Option[Idea]]
  def fetchOneByName(questionId: QuestionId, name: String): Future[Option[Idea]]
  def insert(name: String, question: Question): Future[Idea]
  def update(ideaId: IdeaId, name: String, status: IdeaStatus): Future[Int]
}

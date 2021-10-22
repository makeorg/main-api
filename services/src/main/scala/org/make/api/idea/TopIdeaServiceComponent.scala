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

import org.make.core.idea.{IdeaId, TopIdea, TopIdeaId, TopIdeaScores}
import org.make.core.question.QuestionId
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait TopIdeaService {
  def create(
    ideaId: IdeaId,
    questionId: QuestionId,
    name: String,
    label: String,
    scores: TopIdeaScores,
    weight: Float
  ): Future[TopIdea]
  def getById(topIdeaId: TopIdeaId): Future[Option[TopIdea]]
  def search(
    start: Start = Start.zero,
    end: Option[End] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    ideaId: Option[IdeaId],
    questionIds: Option[Seq[QuestionId]],
    name: Option[String]
  ): Future[Seq[TopIdea]]
  def update(topIdea: TopIdea): Future[TopIdea]
  def delete(topIdeaId: TopIdeaId): Future[Unit]
  def count(ideaId: Option[IdeaId], questionId: Option[QuestionId], name: Option[String]): Future[Int]
}

trait TopIdeaServiceComponent {
  def topIdeaService: TopIdeaService
}

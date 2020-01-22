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
import org.make.api.technical.IdGeneratorComponent
import org.make.core.idea.{IdeaId, TopIdea, TopIdeaId, TopIdeaScores}
import org.make.core.question.QuestionId

import scala.concurrent.Future

trait TopIdeaService {
  def create(ideaId: IdeaId,
             questionId: QuestionId,
             name: String,
             label: String,
             scores: TopIdeaScores,
             weight: Float): Future[TopIdea]
  def getById(topIdeaId: TopIdeaId): Future[Option[TopIdea]]
  def search(start: Int = 0,
             end: Option[Int] = None,
             sort: Option[String] = None,
             order: Option[String] = None,
             ideaId: Option[IdeaId],
             questionId: Option[QuestionId],
             name: Option[String]): Future[Seq[TopIdea]]
  def update(topIdea: TopIdea): Future[TopIdea]
  def delete(topIdeaId: TopIdeaId): Future[Unit]
  def count(ideaId: Option[IdeaId], questionId: Option[QuestionId], name: Option[String]): Future[Int]
}

trait TopIdeaServiceComponent {
  def topIdeaService: TopIdeaService
}

trait DefaultTopIdeaServiceComponent extends TopIdeaServiceComponent {
  self: PersistentTopIdeaServiceComponent with IdGeneratorComponent =>

  override val topIdeaService: DefaultTopIdeaService = new DefaultTopIdeaService

  class DefaultTopIdeaService extends TopIdeaService {

    override def create(ideaId: IdeaId,
                        questionId: QuestionId,
                        name: String,
                        label: String,
                        scores: TopIdeaScores,
                        weight: Float): Future[TopIdea] = {
      persistentTopIdeaService.persist(
        TopIdea(idGenerator.nextTopIdeaId(), ideaId, questionId, name, label, scores, weight)
      )
    }

    override def getById(topIdeaId: TopIdeaId): Future[Option[TopIdea]] = {
      persistentTopIdeaService.getById(topIdeaId)
    }

    override def update(topIdea: TopIdea): Future[TopIdea] = {
      persistentTopIdeaService.modify(topIdea)
    }

    override def search(start: Int = 0,
                        end: Option[Int] = None,
                        sort: Option[String] = None,
                        order: Option[String] = None,
                        ideaId: Option[IdeaId],
                        questionId: Option[QuestionId],
                        name: Option[String]): Future[Seq[TopIdea]] = {
      persistentTopIdeaService.search(start, end, sort, order, ideaId, questionId, name)
    }

    override def delete(topIdeaId: TopIdeaId): Future[Unit] = {
      persistentTopIdeaService.remove(topIdeaId)
    }

    override def count(ideaId: Option[IdeaId], questionId: Option[QuestionId], name: Option[String]): Future[Int] = {
      persistentTopIdeaService.count(ideaId, questionId, name)
    }
  }
}

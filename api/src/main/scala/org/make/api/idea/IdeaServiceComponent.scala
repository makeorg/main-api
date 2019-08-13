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

import org.make.api.idea.IdeaEvent.{IdeaCreatedEvent, IdeaUpdatedEvent}
import org.make.api.idea.IdeaExceptions.{IdeaAlreadyExistsException, IdeaDoesnotExistsException}
import org.make.api.technical.{EventBusServiceComponent, IdGeneratorComponent, ShortenedNames}
import org.make.core.DateHelper
import org.make.core.idea.indexed.IdeaSearchResult
import org.make.core.idea.{Idea, IdeaId, IdeaSearchQuery, IdeaStatus}
import org.make.core.question.{Question, QuestionId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait IdeaServiceComponent {
  def ideaService: IdeaService
}

trait IdeaService extends ShortenedNames {
  def fetchAll(ideaSearchQuery: IdeaSearchQuery): Future[IdeaSearchResult]
  def fetchAllByIdeaIds(ids: Seq[IdeaId]): Future[Seq[Idea]]
  def fetchOne(ideaId: IdeaId): Future[Option[Idea]]
  def fetchOneByName(questionId: QuestionId, name: String): Future[Option[Idea]]
  def insert(name: String, question: Question): Future[Idea]
  def update(ideaId: IdeaId, name: String, status: IdeaStatus): Future[Int]
}

trait DefaultIdeaServiceComponent extends IdeaServiceComponent {
  this: PersistentIdeaServiceComponent
    with EventBusServiceComponent
    with IdeaSearchEngineComponent
    with IdGeneratorComponent =>

  override val ideaService: IdeaService = new DefaultIdeaService

  class DefaultIdeaService extends IdeaService with ShortenedNames {

    override def fetchAll(ideaSearchQuery: IdeaSearchQuery): Future[IdeaSearchResult] = {
      elasticsearchIdeaAPI.searchIdeas(ideaSearchQuery)
    }

    override def fetchAllByIdeaIds(ids: Seq[IdeaId]): Future[Seq[Idea]] = {
      persistentIdeaService.findAllByIdeaIds(ids)
    }

    override def fetchOne(ideaId: IdeaId): Future[Option[Idea]] = {
      persistentIdeaService.findOne(ideaId)
    }

    override def fetchOneByName(questionId: QuestionId, name: String): Future[Option[Idea]] = {
      persistentIdeaService.findOneByName(questionId, name)
    }

    override def insert(name: String, question: Question): Future[Idea] = {
      val idea: Idea =
        Idea(
          ideaId = idGenerator.nextIdeaId(),
          name = name,
          language = Some(question.language),
          country = Some(question.country),
          question = Some(question.question),
          questionId = Some(question.questionId),
          operationId = question.operationId,
          themeId = question.themeId,
          createdAt = Some(DateHelper.now()),
          updatedAt = Some(DateHelper.now())
        )
      persistentIdeaService.findOneByName(question.questionId, name).flatMap { result =>
        if (result.isDefined) {
          Future.failed(IdeaAlreadyExistsException(idea.name))
        } else {
          persistentIdeaService.persist(idea).map { idea =>
            eventBusService.publish(IdeaCreatedEvent(ideaId = idea.ideaId, eventDate = DateHelper.now()))
            idea
          }

        }
      }
    }

    override def update(ideaId: IdeaId, name: String, status: IdeaStatus): Future[Int] = {
      persistentIdeaService.findOne(ideaId).flatMap { result =>
        if (result.isEmpty) {
          Future.failed(IdeaDoesnotExistsException(ideaId.value))
        } else {
          persistentIdeaService.modify(ideaId = ideaId, name = name, status = status).map { idea =>
            eventBusService.publish(IdeaUpdatedEvent(ideaId = ideaId, eventDate = DateHelper.now()))
            idea
          }
        }
      }
    }
  }
}

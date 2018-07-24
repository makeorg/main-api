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

import java.util.UUID

import org.make.api.idea.IdeaEvent.{IdeaCreatedEvent, IdeaUpdatedEvent}
import org.make.api.idea.IdeaExceptions.{IdeaAlreadyExistsException, IdeaDoesnotExistsException}
import org.make.api.technical.{EventBusServiceComponent, ShortenedNames}
import org.make.core.DateHelper
import org.make.core.idea.indexed.IdeaSearchResult
import org.make.core.idea.{Idea, IdeaId, IdeaSearchQuery}
import org.make.core.operation.OperationId
import org.make.core.reference.{Country, Language, ThemeId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait IdeaServiceComponent {
  def ideaService: IdeaService
}

trait IdeaService extends ShortenedNames {
  def fetchAll(ideaSearchQuery: IdeaSearchQuery): Future[IdeaSearchResult]
  def fetchAllByIdeaIds(ids: Seq[IdeaId]): Future[Seq[Idea]]
  def fetchOne(ideaId: IdeaId): Future[Option[Idea]]
  def fetchOneByName(name: String): Future[Option[Idea]]
  def insert(name: String,
             language: Option[Language],
             country: Option[Country],
             operationId: Option[OperationId],
             themeId: Option[ThemeId],
             question: Option[String]): Future[Idea]
  def update(ideaId: IdeaId, name: String): Future[Int]
}

trait DefaultIdeaServiceComponent extends IdeaServiceComponent with ShortenedNames {
  this: PersistentIdeaServiceComponent with EventBusServiceComponent with IdeaSearchEngineComponent =>

  override val ideaService: IdeaService = new IdeaService {

    override def fetchAll(ideaSearchQuery: IdeaSearchQuery): Future[IdeaSearchResult] = {
      elasticsearchIdeaAPI.searchIdeas(ideaSearchQuery)
    }

    override def fetchAllByIdeaIds(ids: Seq[IdeaId]): Future[Seq[Idea]] = {
      persistentIdeaService.findAllByIdeaIds(ids)
    }

    override def fetchOne(ideaId: IdeaId): Future[Option[Idea]] = {
      persistentIdeaService.findOne(ideaId)
    }

    override def fetchOneByName(name: String): Future[Option[Idea]] = {
      persistentIdeaService.findOneByName(name)
    }

    override def insert(name: String,
                        language: Option[Language],
                        country: Option[Country],
                        operationId: Option[OperationId],
                        themeId: Option[ThemeId],
                        question: Option[String]): Future[Idea] = {
      val idea: Idea =
        Idea(
          ideaId = IdeaId(UUID.randomUUID().toString),
          name = name,
          language = language,
          country = country,
          question = question,
          operationId = operationId,
          themeId = themeId,
          createdAt = Some(DateHelper.now()),
          updatedAt = Some(DateHelper.now())
        )
      persistentIdeaService.findOneByName(name).flatMap { result =>
        if (result.isDefined) {
          Future.failed(IdeaAlreadyExistsException(idea.name))
        } else {
          persistentIdeaService.persist(idea).map { idea =>
            eventBusService.publish(IdeaCreatedEvent(ideaId = idea.ideaId))
            idea
          }

        }
      }
    }

    override def update(ideaId: IdeaId, name: String): Future[Int] = {
      persistentIdeaService.findOne(ideaId).flatMap { result =>
        if (result.isEmpty) {
          Future.failed(IdeaDoesnotExistsException(ideaId.value))
        } else {
          persistentIdeaService.modify(ideaId = ideaId, name = name).map { idea =>
            eventBusService.publish(IdeaUpdatedEvent(ideaId = ideaId))
            idea
          }
        }
      }
    }
  }
}

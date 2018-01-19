package org.make.api.idea

import java.util.UUID

import org.make.api.idea.IdeaEvent.{IdeaCreatedEvent, IdeaUpdatedEvent}
import org.make.api.idea.IdeaExceptions.{IdeaAlreadyExistsException, IdeaDoesnotExistsException}
import org.make.api.technical.{EventBusServiceComponent, ShortenedNames}
import org.make.core.DateHelper
import org.make.core.idea.indexed.IdeaSearchResult
import org.make.core.idea.{Idea, IdeaId, IdeaSearchQuery}
import org.make.core.operation.OperationId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait IdeaServiceComponent {
  def ideaService: IdeaService
}

trait IdeaService extends ShortenedNames {
  def fetchAll(ideaSearchQuery: IdeaSearchQuery): Future[IdeaSearchResult]
  def fetchOne(ideaId: IdeaId): Future[Option[Idea]]
  def fetchOneByName(name: String): Future[Option[Idea]]
  def insert(name: String,
             language: Option[String],
             country: Option[String],
             operationId: Option[OperationId],
             question: Option[String]): Future[Idea]
  def update(ideaId: IdeaId, name: String): Future[Int]
}

trait DefaultIdeaServiceComponent extends IdeaServiceComponent with ShortenedNames {
  this: PersistentIdeaServiceComponent
  with EventBusServiceComponent
  with IdeaSearchEngineComponent =>

  override val ideaService: IdeaService = new IdeaService {

    override def fetchAll(ideaSearchQuery: IdeaSearchQuery): Future[IdeaSearchResult] = {
      elasticsearchIdeaAPI.searchIdeas(ideaSearchQuery)
    }

    override def fetchOne(ideaId: IdeaId): Future[Option[Idea]] = {
      persistentIdeaService.findOne(ideaId)
    }

    override def fetchOneByName(name: String): Future[Option[Idea]] = {
      persistentIdeaService.findOneByName(name)
    }

    override def insert(name: String,
                        language: Option[String],
                        country: Option[String],
                        operationId: Option[OperationId],
                        question: Option[String]): Future[Idea] = {
      val idea: Idea =
        Idea(
          ideaId = IdeaId(UUID.randomUUID().toString),
          name = name,
          language = language,
          country = country,
          question = question,
          operationId = operationId,
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

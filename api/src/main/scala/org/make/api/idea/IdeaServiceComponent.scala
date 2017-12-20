package org.make.api.idea

import org.make.api.idea.IdeaExceptions.{IdeaAlreadyExistsException, IdeaDoesnotExistsException}
import org.make.api.technical.ShortenedNames
import org.make.core.reference._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait IdeaServiceComponent {
  def ideaService: IdeaService
}

trait IdeaService extends ShortenedNames {
  def fetchAll(ideaFilters: IdeaFiltersRequest): Future[Seq[Idea]]
  def fetchOne(ideaId: IdeaId): Future[Option[Idea]]
  def fetchOneByName(name: String): Future[Option[Idea]]
  def insert(name: String,
             language: Option[String],
             country: Option[String],
             operation: Option[String],
             question: Option[String]): Future[Idea]
  def update(ideaId: IdeaId, name: String): Future[Int]
}

trait DefaultIdeaServiceComponent extends IdeaServiceComponent with ShortenedNames {
  this: PersistentIdeaServiceComponent =>

  override val ideaService: IdeaService = new IdeaService {

    override def fetchAll(ideaFilters: IdeaFiltersRequest): Future[Seq[Idea]] = {
      persistentIdeaService.findAll(ideaFilters)
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
                        operation: Option[String],
                        question: Option[String]): Future[Idea] = {
      val idea: Idea =
        Idea(name = name, language = language, country = country, operation = operation, question = question)
      persistentIdeaService.findOneByName(name).flatMap { result =>
        if (result.isDefined) {
          Future.failed(IdeaAlreadyExistsException(idea.name))
        } else {
          persistentIdeaService.persist(idea)
        }
      }
    }

    override def update(ideaId: IdeaId, name: String): Future[Int] = {
      persistentIdeaService.findOne(ideaId).flatMap { result =>
        if (result.isEmpty) {
          Future.failed(IdeaDoesnotExistsException(ideaId.value))
        } else {
          persistentIdeaService.modify(ideaId = ideaId, name = name)
        }
      }
    }
  }
}

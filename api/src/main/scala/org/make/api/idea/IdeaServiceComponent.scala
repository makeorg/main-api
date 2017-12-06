package org.make.api.idea

import org.make.api.idea.IdeaExceptions.IdeaAlreadyExistsException
import org.make.api.proposal.ProposalSearchEngineComponent
import org.make.api.technical.ShortenedNames
import org.make.core.reference._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait IdeaServiceComponent {
  def ideaService: IdeaService
}

trait IdeaService extends ShortenedNames {
  def fetchAll(): Future[Seq[Idea]]
  def fetchOneByName(name: String): Future[Option[Idea]]
  def insert(name: String,
             language: Option[String],
             country: Option[String],
             operation: Option[String],
             question: Option[String]): Future[Idea]
  def update(name: String): Future[Idea]
}

trait DefaultIdeaServiceComponent extends IdeaServiceComponent with ShortenedNames {
  this: PersistentIdeaServiceComponent with ProposalSearchEngineComponent =>

  val ideaService = new IdeaService {

    override def fetchAll(): Future[Seq[Idea]] = {
      persistentIdeaService.findAll()
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

    override def update(name: String): Future[Idea] = ???
  }
}

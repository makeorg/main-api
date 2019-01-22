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
import org.make.api.question.PersistentQuestionServiceComponent
import org.make.api.tag.TagServiceComponent
import org.make.api.technical.IdGeneratorComponent
import org.make.core.{DateHelper, ValidationError, ValidationFailedError}
import org.make.core.idea.{Idea, IdeaId, IdeaStatus}
import org.make.core.question.{Question, QuestionId}
import org.make.core.tag.TagId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait IdeaMappingService {
  def create(questionId: QuestionId,
             stakeTagId: Option[TagId],
             solutionTypeTagId: Option[TagId],
             ideaId: IdeaId): Future[IdeaMapping]
  def getById(ideaMappingId: IdeaMappingId): Future[Option[IdeaMapping]]
  def changeIdea(IdeaMappingId: IdeaMappingId, newIdea: IdeaId, migrateProposals: Boolean): Future[Option[IdeaMapping]]
  def search(questionId: Option[QuestionId],
             stakeTagId: Option[TagIdOrNone],
             solutionTypeTagId: Option[TagIdOrNone],
             ideaId: Option[IdeaId]): Future[Seq[IdeaMapping]]
  def getOrCreateMapping(questionId: QuestionId,
                         stakeTagId: Option[TagId],
                         solutionTypeTagId: Option[TagId]): Future[IdeaMapping]
}

trait IdeaMappingServiceComponent {
  def ideaMappingService: IdeaMappingService
}

trait DefaultIdeaMappingServiceComponent extends IdeaMappingServiceComponent {
  self: PersistentIdeaMappingServiceComponent
    with PersistentIdeaServiceComponent
    with TagServiceComponent
    with PersistentQuestionServiceComponent
    with IdGeneratorComponent =>
  override val ideaMappingService: IdeaMappingService = new IdeaMappingService {

    override def create(questionId: QuestionId,
                        stakeTagId: Option[TagId],
                        solutionTypeTagId: Option[TagId],
                        ideaId: IdeaId): Future[IdeaMapping] = {
      persistentIdeaMappingService.persist(
        IdeaMapping(idGenerator.nextIdeaMappingId(), questionId, stakeTagId, solutionTypeTagId, ideaId)
      )
    }

    override def getById(ideaMappingId: IdeaMappingId): Future[Option[IdeaMapping]] = {
      persistentIdeaMappingService.get(ideaMappingId)
    }

    override def changeIdea(ideaMappingId: IdeaMappingId,
                            newIdea: IdeaId,
                            migrateProposals: Boolean): Future[Option[IdeaMapping]] = {
      persistentIdeaMappingService.get(ideaMappingId).flatMap {
        case None => Future.successful(None)
        case Some(mapping) =>
          persistentIdeaMappingService.updateMapping(mapping.copy(ideaId = newIdea))
      }

    }

    override def search(questionId: Option[QuestionId],
                        stakeTagId: Option[TagIdOrNone],
                        solutionTypeTagId: Option[TagIdOrNone],
                        ideaId: Option[IdeaId]): Future[Seq[IdeaMapping]] = {
      persistentIdeaMappingService.find(questionId, stakeTagId, solutionTypeTagId, ideaId)
    }

    override def getOrCreateMapping(questionId: QuestionId,
                                    stakeTagId: Option[TagId],
                                    solutionTypeTagId: Option[TagId]): Future[IdeaMapping] = {

      persistentIdeaMappingService
        .find(Some(questionId), optionToTagIdOrNone(stakeTagId), optionToTagIdOrNone(solutionTypeTagId), None)
        .flatMap {
          case Seq()        => createMapping(questionId, stakeTagId, solutionTypeTagId)
          case Seq(mapping) => Future.successful(mapping)
          case other        => Future.successful(other.head)
        }
    }

    private def optionToTagIdOrNone(maybeTagId: Option[TagId]): Option[TagIdOrNone] = {
      maybeTagId match {
        case Some(tagId) => Some(Right(tagId))
        case None        => Some(Left(None))
      }
    }
    private def createMapping(questionId: QuestionId,
                              stakeTagId: Option[TagId],
                              solutionTypeTagId: Option[TagId]): Future[IdeaMapping] = {

      for {
        question <- retrieveQuestionOrFail(questionId)
        name     <- computeIdeaName(stakeTagId, solutionTypeTagId, question.slug)
        idea     <- createIdea(questionId, question, name)
        mapping <- persistentIdeaMappingService
          .persist(IdeaMapping(idGenerator.nextIdeaMappingId(), questionId, stakeTagId, solutionTypeTagId, idea.ideaId))
      } yield mapping
    }

    private def computeIdeaName(stake: Option[TagId], solution: Option[TagId], questionSlug: String): Future[String] = {
      val tags = Seq(stake, solution).flatten
      tagService
        .findByTagIds(tags)
        .map { tags =>
          val tagMap = tags.map(tag => Some(tag.tagId) -> tag.label).toMap[Option[TagId], String]
          s"${tagMap.getOrElse(stake, "None")} / ${tagMap.getOrElse(solution, s"None")} ($questionSlug)"
        }
    }
    private def retrieveQuestionOrFail(questionId: QuestionId): Future[Question] = {
      persistentQuestionService
        .getById(questionId)
        .flatMap {
          case None =>
            Future.failed(
              ValidationFailedError(
                Seq(ValidationError("questionId", Some(s"Question ${questionId.value} doesn't exist")))
              )
            )
          case Some(question) =>
            Future.successful(question)
        }
    }
    private def createIdea(questionId: QuestionId, question: Question, label: String): Future[Idea] = {
      persistentIdeaService.persist(
        Idea(
          idGenerator.nextIdeaId(),
          label,
          Some(question.language),
          Some(question.country),
          Some(question.question),
          None,
          Some(questionId),
          None,
          IdeaStatus.Activated,
          Some(DateHelper.now()),
          Some(DateHelper.now())
        )
      )
    }
  }
}

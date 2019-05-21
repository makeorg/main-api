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

package org.make.api.question

import org.make.api.technical.IdGeneratorComponent
import org.make.core.operation.OperationId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import org.make.core.{ValidationError, ValidationFailedError}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait QuestionService {
  def getQuestion(questionId: QuestionId): Future[Option[Question]]
  def getQuestions(questionIds: Seq[QuestionId]): Future[Seq[Question]]
  def getQuestionByQuestionIdValueOrSlug(questionIdValueOrSlug: String): Future[Option[Question]]
  def findQuestion(maybeThemeId: Option[ThemeId],
                   maybeOperationId: Option[OperationId],
                   country: Country,
                   language: Language): Future[Option[Question]]
  def searchQuestion(request: SearchQuestionRequest): Future[Seq[Question]]
  def countQuestion(request: SearchQuestionRequest): Future[Int]
  def findQuestionByQuestionIdOrThemeOrOperation(maybeQuestionId: Option[QuestionId],
                                                 maybeThemeId: Option[ThemeId],
                                                 maybeOperationId: Option[OperationId],
                                                 country: Country,
                                                 language: Language): Future[Option[Question]]
  def createQuestion(country: Country, language: Language, question: String, slug: String): Future[Question]
}

case class SearchQuestionRequest(maybeQuestionIds: Option[Seq[QuestionId]] = None,
                                 maybeThemeId: Option[ThemeId] = None,
                                 maybeOperationId: Option[OperationId] = None,
                                 country: Option[Country] = None,
                                 language: Option[Language] = None,
                                 maybeSlug: Option[String] = None,
                                 skip: Option[Int] = None,
                                 limit: Option[Int] = None,
                                 sort: Option[String] = None,
                                 order: Option[String] = None)

trait QuestionServiceComponent {
  def questionService: QuestionService
}

trait DefaultQuestionService extends QuestionServiceComponent {
  this: PersistentQuestionServiceComponent with IdGeneratorComponent =>

  override lazy val questionService: QuestionService = new DefaultQuestionService

  class DefaultQuestionService extends QuestionService {

    override def getQuestions(questionIds: Seq[QuestionId]): Future[Seq[Question]] = {
      persistentQuestionService.getByIds(questionIds)
    }

    override def getQuestionByQuestionIdValueOrSlug(questionIdValueOrSlug: String): Future[Option[Question]] = {
      persistentQuestionService.getByQuestionIdValueOrSlug(questionIdValueOrSlug)
    }

    override def getQuestion(questionId: QuestionId): Future[Option[Question]] = {
      persistentQuestionService.getById(questionId)
    }

    override def findQuestion(maybeThemeId: Option[ThemeId],
                              maybeOperationId: Option[OperationId],
                              country: Country,
                              language: Language): Future[Option[Question]] = {

      (maybeOperationId, maybeThemeId) match {
        case (None, None) =>
          Future.failed(
            ValidationFailedError(
              Seq(ValidationError("unknown", Some("operationId or themeId must be provided to question search")))
            )
          )
        case (Some(_), Some(_)) =>
          Future.failed(
            ValidationFailedError(
              Seq(
                ValidationError(
                  "unknown",
                  Some("You must provide only one of themeId or operationId to question search")
                )
              )
            )
          )
        case _ =>
          persistentQuestionService
            .find(
              SearchQuestionRequest(
                country = Some(country),
                language = Some(language),
                maybeOperationId = maybeOperationId,
                maybeThemeId = maybeThemeId
              )
            )
            .map(_.headOption)
      }

    }
    override def findQuestionByQuestionIdOrThemeOrOperation(maybeQuestionId: Option[QuestionId],
                                                            maybeThemeId: Option[ThemeId],
                                                            maybeOperationId: Option[OperationId],
                                                            country: Country,
                                                            language: Language): Future[Option[Question]] = {

      // If all ids are None, return None
      maybeQuestionId
        .orElse(maybeOperationId)
        .orElse(maybeThemeId)
        .map { _ =>
          maybeQuestionId.map(getQuestion).getOrElse(findQuestion(maybeThemeId, maybeOperationId, country, language))
        }
        .getOrElse(Future.successful(None))
    }

    override def searchQuestion(request: SearchQuestionRequest): Future[Seq[Question]] = {
      persistentQuestionService.find(request)
    }

    override def countQuestion(request: SearchQuestionRequest): Future[Int] = {
      persistentQuestionService.count(request)
    }

    override def createQuestion(country: Country,
                                language: Language,
                                question: String,
                                slug: String): Future[Question] = {
      persistentQuestionService.persist(
        Question(
          questionId = idGenerator.nextQuestionId(),
          slug = slug,
          country = country,
          language = language,
          question = question,
          operationId = None,
          themeId = None
        )
      )
    }
  }
}

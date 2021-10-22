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

package org.make.api.operation

import cats.data.NonEmptyList
import enumeratum.values.{StringCirceEnum, StringEnum, StringEnumEntry}
import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.MaxSize
import org.make.core.elasticsearch.IndexationStatus
import org.make.core.operation._
import org.make.core.operation.indexed.OperationOfQuestionSearchResult
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.technical.Pagination._
import org.make.core.Order

import java.time.ZonedDateTime
import scala.concurrent.Future

trait OperationOfQuestionService {
  // TODO: do we really need all these to be separate?
  def findByQuestionId(questionId: QuestionId): Future[Option[OperationOfQuestion]]
  def findByOperationId(operationId: OperationId): Future[Seq[OperationOfQuestion]]
  def findByQuestionSlug(slug: String): Future[Option[OperationOfQuestion]]
  def find(
    start: Start = Start.zero,
    end: Option[End] = None,
    sort: Option[String] = None,
    order: Option[Order] = None,
    request: SearchOperationsOfQuestions = SearchOperationsOfQuestions()
  ): Future[Seq[OperationOfQuestion]]
  def search(searchQuery: OperationOfQuestionSearchQuery): Future[OperationOfQuestionSearchResult]
  def updateWithQuestion(operationOfQuestion: OperationOfQuestion, question: Question): Future[OperationOfQuestion]
  def update(operationOfQuestion: OperationOfQuestion): Future[OperationOfQuestion]
  def count(request: SearchOperationsOfQuestions): Future[Int]
  def count(query: OperationOfQuestionSearchQuery): Future[Long]

  /**
    * Deletes an OperationOfQuestion and all its associated objects:
    * - The associated Question
    * - The associated sequence configuration
    * @param questionId the operationOfQuestion to delete
    * @return a future to follow the completion
    */
  def delete(questionId: QuestionId): Future[Unit]

  /**
    * This function will:
    * - Create a new question
    * - Create a new sequence for this question
    * - Create a new OperationOfQuestion
    * @param parameters all needed parameters to create everything
    * @return the created OperationOfQuestion
    */
  def create(parameters: CreateOperationOfQuestion): Future[OperationOfQuestion]

  def indexById(questionId: QuestionId): Future[Option[IndexationStatus]]
  def getQuestionsInfos(
    questionIds: Option[Seq[QuestionId]],
    moderationMode: ModerationMode,
    minVotesCount: Option[Int],
    minScore: Option[Double]
  ): Future[Seq[ModerationOperationOfQuestionInfosResponse]]
}

final case class CreateOperationOfQuestion(
  operationId: OperationId,
  startDate: ZonedDateTime,
  endDate: ZonedDateTime,
  operationTitle: String,
  slug: String,
  countries: NonEmptyList[Country],
  language: Language,
  question: String,
  shortTitle: Option[String],
  consultationImage: Option[String],
  consultationImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  descriptionImage: Option[String],
  descriptionImageAlt: Option[String Refined MaxSize[W.`130`.T]],
  actions: Option[String],
  featured: Boolean
)

final case class SearchOperationsOfQuestions(
  questionIds: Option[Seq[QuestionId]] = None,
  operationIds: Option[Seq[OperationId]] = None,
  operationKind: Option[Seq[OperationKind]] = None,
  openAt: Option[ZonedDateTime] = None,
  endAfter: Option[ZonedDateTime] = None,
  slug: Option[String] = None
)

trait OperationOfQuestionServiceComponent {
  def operationOfQuestionService: OperationOfQuestionService
}

sealed abstract class ModerationMode(val value: String) extends StringEnumEntry with Product with Serializable

object ModerationMode extends StringEnum[ModerationMode] with StringCirceEnum[ModerationMode] {

  case object Enrichment extends ModerationMode("Enrichment")
  case object Moderation extends ModerationMode("Moderation")

  override def values: IndexedSeq[ModerationMode] = findValues
}

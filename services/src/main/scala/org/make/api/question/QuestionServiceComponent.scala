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

import cats.data.NonEmptyList
import org.make.core.idea.{TopIdea, TopIdeaId}
import org.make.core.operation.OperationId
import org.make.core.personality.PersonalityRoleId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user._
import org.make.core.user.indexed.OrganisationSearchResult

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait QuestionService {
  def getCachedQuestion(questionId: QuestionId): Future[Option[Question]]
  def getQuestion(questionId: QuestionId): Future[Option[Question]]
  def getQuestions(questionIds: Seq[QuestionId]): Future[Seq[Question]]
  def getQuestionByQuestionIdValueOrSlug(questionIdValueOrSlug: String): Future[Option[Question]]
  def findQuestion(
    maybeOperationId: Option[OperationId],
    country: Country,
    language: Language
  ): Future[Option[Question]]
  def searchQuestion(request: SearchQuestionRequest): Future[Seq[Question]]
  def countQuestion(request: SearchQuestionRequest): Future[Int]
  def createQuestion(
    countries: NonEmptyList[Country],
    language: Language,
    question: String,
    shortTitle: Option[String],
    slug: String
  ): Future[Question]
  def getQuestionPersonalities(
    start: Start,
    end: Option[End],
    questionId: QuestionId,
    personalityRoleId: Option[PersonalityRoleId]
  ): Future[Seq[QuestionPersonalityResponse]]
  def getPartners(
    questionId: QuestionId,
    organisationIds: Seq[UserId],
    sortAlgorithm: Option[OrganisationSortAlgorithm],
    limit: Option[Int],
    skip: Option[Int]
  ): Future[OrganisationSearchResult]
  def getTopIdeas(
    start: Start,
    end: Option[End],
    seed: Option[Int],
    questionId: QuestionId
  ): Future[QuestionTopIdeasResponseWithSeed]
  def getTopIdea(
    topIdeaId: TopIdeaId,
    questionId: QuestionId,
    seed: Option[Int]
  ): Future[Option[QuestionTopIdeaResultWithSeed]]
}

trait QuestionServiceComponent {
  def questionService: QuestionService
}

final case class QuestionTopIdeaResultWithSeed(topIdea: TopIdea, avatars: Seq[String], proposalsCount: Int, seed: Int)

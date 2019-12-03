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

package org.make.api.personality

import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.core.personality.{Personality, PersonalityId, PersonalityRole}
import org.make.core.question.QuestionId
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait QuestionPersonalityServiceComponent {
  def questionPersonalityService: QuestionPersonalityService
}

trait QuestionPersonalityService extends ShortenedNames {
  def getPersonality(personalityId: PersonalityId): Future[Option[Personality]]
  def find(start: Int,
           end: Option[Int],
           sort: Option[String],
           order: Option[String],
           userId: Option[UserId],
           questionId: Option[QuestionId],
           personalityRole: Option[PersonalityRole]): Future[Seq[Personality]]
  def count(userId: Option[UserId],
            questionId: Option[QuestionId],
            personalityRole: Option[PersonalityRole]): Future[Int]
  def createPersonality(request: CreateQuestionPersonalityRequest): Future[Personality]
  def updatePersonality(personalityId: PersonalityId,
                        request: UpdateQuestionPersonalityRequest): Future[Option[Personality]]
  def deletePersonality(personalityId: PersonalityId): Future[Unit]
}

trait DefaultQuestionPersonalityServiceComponent extends QuestionPersonalityServiceComponent {
  this: PersistentQuestionPersonalityServiceComponent with IdGeneratorComponent =>

  override lazy val questionPersonalityService: DefaultQuestionPersonalityService =
    new DefaultQuestionPersonalityService

  class DefaultQuestionPersonalityService extends QuestionPersonalityService {

    override def getPersonality(personalityId: PersonalityId): Future[Option[Personality]] = {
      persistentQuestionPersonalityService.getById(personalityId)
    }

    override def createPersonality(request: CreateQuestionPersonalityRequest): Future[Personality] = {
      val personality: Personality = Personality(
        personalityId = idGenerator.nextPersonalityId(),
        userId = request.userId,
        questionId = request.questionId,
        personalityRole = request.personalityRole
      )
      persistentQuestionPersonalityService.persist(personality)
    }

    override def updatePersonality(personalityId: PersonalityId,
                                   request: UpdateQuestionPersonalityRequest): Future[Option[Personality]] = {
      persistentQuestionPersonalityService.getById(personalityId).flatMap {
        case Some(personality) =>
          persistentQuestionPersonalityService
            .modify(personality.copy(userId = request.userId, personalityRole = request.personalityRole))
            .map(Some.apply)
        case None => Future.successful(None)
      }
    }

    override def find(start: Int,
                      end: Option[Int],
                      sort: Option[String],
                      order: Option[String],
                      userId: Option[UserId],
                      questionId: Option[QuestionId],
                      personalityRole: Option[PersonalityRole]): Future[Seq[Personality]] = {
      persistentQuestionPersonalityService.find(start, end, sort, order, userId, questionId, personalityRole)
    }

    override def count(userId: Option[UserId],
                       questionId: Option[QuestionId],
                       personalityRole: Option[PersonalityRole]): Future[Int] = {
      persistentQuestionPersonalityService.count(userId, questionId, personalityRole)
    }

    override def deletePersonality(personalityId: PersonalityId): Future[Unit] = {
      persistentQuestionPersonalityService.delete(personalityId)
    }

  }
}

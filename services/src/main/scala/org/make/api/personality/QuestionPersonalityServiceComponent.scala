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

import org.make.core.personality.{Personality, PersonalityId, PersonalityRoleId}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait QuestionPersonalityServiceComponent {
  def questionPersonalityService: QuestionPersonalityService
}

trait QuestionPersonalityService {
  def getPersonality(personalityId: PersonalityId): Future[Option[Personality]]
  def find(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    userId: Option[UserId],
    questionId: Option[QuestionId],
    personalityRoleId: Option[PersonalityRoleId]
  ): Future[Seq[Personality]]
  def count(
    userId: Option[UserId],
    questionId: Option[QuestionId],
    personalityRoleId: Option[PersonalityRoleId]
  ): Future[Int]
  def createPersonality(request: CreateQuestionPersonalityRequest): Future[Personality]
  def updatePersonality(
    personalityId: PersonalityId,
    request: UpdateQuestionPersonalityRequest
  ): Future[Option[Personality]]
  def deletePersonality(personalityId: PersonalityId): Future[Unit]
  def getPersonalitiesOpinionsByQuestions(personalities: Seq[Personality]): Future[Seq[PersonalityOpinionResponse]]
}

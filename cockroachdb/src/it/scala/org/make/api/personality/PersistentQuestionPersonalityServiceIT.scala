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

import cats.data.NonEmptyList
import org.make.api.DatabaseTest
import org.make.api.TestUtils
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.core.personality.{Personality, PersonalityId, PersonalityRole, PersonalityRoleId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import org.make.core.technical.Pagination.Start

class PersistentQuestionPersonalityServiceIT
    extends DatabaseTest
    with DefaultPersistentQuestionPersonalityServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentPersonalityRoleServiceComponent {

  val personality: Personality = Personality(
    personalityId = PersonalityId("personality"),
    userId = UserId("user-id"),
    personalityRoleId = PersonalityRoleId("candidate"),
    questionId = QuestionId("question")
  )

  val question = Question(
    questionId = QuestionId("question"),
    slug = "question",
    countries = NonEmptyList.of(Country("FR")),
    language = Language("fr"),
    question = "question ?",
    shortTitle = None,
    operationId = None
  )

  val user = TestUtils.user(id = UserId("user-id"))

  Feature("get personality by id") {
    Scenario("get existing personality") {
      val futurePersonality = for {
        _ <- persistentQuestionService.persist(question)
        _ <- persistentUserService.persist(user)
        _ <- persistentPersonalityRoleService.persist(
          PersonalityRole(PersonalityRoleId("candidate"), name = "CANDIDATE_TEST")
        )
        _           <- persistentQuestionPersonalityService.persist(personality)
        personality <- persistentQuestionPersonalityService.getById(PersonalityId("personality"))
      } yield personality

      whenReady(futurePersonality, Timeout(2.seconds)) { personality =>
        personality.map(_.personalityId) should be(Some(PersonalityId("personality")))
      }
    }

    Scenario("get non existing personality") {
      whenReady(persistentQuestionPersonalityService.getById(PersonalityId("not-found")), Timeout(2.seconds)) {
        personality =>
          personality should be(None)
      }
    }
  }

  Feature("search personalities") {
    Scenario("search all") {
      val futurePersonality = for {
        _ <- persistentUserService.persist(user.copy(userId = UserId("user-id-2"), email = "test-2@make.org"))
        _ <- persistentUserService.persist(user.copy(userId = UserId("user-id-3"), email = "test-3@make.org"))
        _ <- persistentQuestionPersonalityService.persist(
          personality.copy(personalityId = PersonalityId("personality2"), userId = UserId("user-id-2"))
        )
        _ <- persistentQuestionPersonalityService.persist(
          personality.copy(personalityId = PersonalityId("personality3"), userId = UserId("user-id-3"))
        )
        personalities <- persistentQuestionPersonalityService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          userId = None,
          questionId = None,
          personalityRoleId = None
        )
      } yield personalities

      whenReady(futurePersonality, Timeout(2.seconds)) { personalities =>
        personalities.map(_.personalityId) should contain(PersonalityId("personality2"))
      }
    }

    Scenario("search by questionId") {
      val futurePersonality = for {
        _ <- persistentQuestionService.persist(question.copy(questionId = QuestionId("question2"), slug = "question-2"))
        _ <- persistentUserService.persist(user.copy(userId = UserId("user-id-4"), email = "test-4@make.org"))
        _ <- persistentUserService.persist(user.copy(userId = UserId("user-id-5"), email = "test-5@make.org"))
        _ <- persistentQuestionPersonalityService.persist(
          personality.copy(
            personalityId = PersonalityId("personality4"),
            questionId = QuestionId("question2"),
            userId = UserId("user-id-4")
          )
        )
        _ <- persistentQuestionPersonalityService.persist(
          personality.copy(
            personalityId = PersonalityId("personality5"),
            questionId = QuestionId("question2"),
            userId = UserId("user-id-5")
          )
        )
        personalities <- persistentQuestionPersonalityService.find(
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          userId = None,
          questionId = Some(QuestionId("question2")),
          personalityRoleId = None
        )
      } yield personalities

      whenReady(futurePersonality, Timeout(2.seconds)) { personalities =>
        personalities.map(_.personalityId) should contain(PersonalityId("personality4"))
      }
    }

  }

  Feature("count personalities") {
    Scenario("count by questionId") {

      val futurePersonalityCount = for {
        _ <- persistentQuestionService.persist(
          question.copy(
            questionId = QuestionId("question-for-count-personality-scenario"),
            slug = "question-count-personality"
          )
        )
        _ <- persistentQuestionPersonalityService.persist(
          personality.copy(
            personalityId = PersonalityId("personality-count-1"),
            questionId = QuestionId("question-for-count-personality-scenario")
          )
        )
        _ <- persistentQuestionPersonalityService.persist(
          personality.copy(
            personalityId = PersonalityId("personality-count-2"),
            questionId = QuestionId("question-for-count-personality-scenario"),
            userId = UserId("user-id-2")
          )
        )
        count <- persistentQuestionPersonalityService.count(
          userId = None,
          questionId = Some(QuestionId("question-for-count-personality-scenario")),
          personalityRoleId = None
        )
      } yield count

      whenReady(futurePersonalityCount, Timeout(2.seconds)) { count =>
        count should be(2)
      }
    }

  }

  Feature("update personalities") {
    Scenario("update existing personality") {
      val updatedPersonality =
        personality.copy(userId = UserId("updated-user"))

      val updatedUser = TestUtils.user(id = UserId("updated-user"), email = "update-user@make.org")

      val futureUpdatedPersonality = for {
        _           <- persistentUserService.persist(updatedUser)
        personality <- persistentQuestionPersonalityService.modify(updatedPersonality)
      } yield personality

      whenReady(futureUpdatedPersonality, Timeout(2.seconds)) { personality =>
        personality.personalityId should be(PersonalityId("personality"))
        personality.userId.value should be("updated-user")
      }
    }
  }

}

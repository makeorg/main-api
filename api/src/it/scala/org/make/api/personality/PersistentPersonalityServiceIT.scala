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

import org.make.api.DatabaseTest
import org.make.api.TestUtilsIT
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.core.personality.{Candidate, Personality, PersonalityId}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.make.core.user.UserId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt

class PersistentPersonalityServiceIT
    extends DatabaseTest
    with DefaultPersistentPersonalityServiceComponent
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentUserServiceComponent {

  override protected val cockroachExposedPort: Int = 40019

  val personality: Personality = Personality(
    personalityId = PersonalityId("personality"),
    userId = UserId("user-id"),
    personalityRole = Candidate,
    questionId = QuestionId("question")
  )

  val question = Question(
    questionId = QuestionId("question"),
    slug = "question",
    country = Country("FR"),
    language = Language("fr"),
    question = "question ?",
    operationId = None,
    themeId = None
  )

  val user = TestUtilsIT.user(id = UserId("user-id"))

  feature("get personality by id") {
    scenario("get existing personality") {
      val futurePersonality = for {
        _           <- persistentQuestionService.persist(question)
        _           <- persistentUserService.persist(user)
        _           <- persistentPersonalityService.persist(personality)
        personality <- persistentPersonalityService.getById(PersonalityId("personality"))
      } yield personality

      whenReady(futurePersonality, Timeout(2.seconds)) { personality =>
        personality.map(_.personalityId) should be(Some(PersonalityId("personality")))
      }
    }

    scenario("get non existing personality") {
      whenReady(persistentPersonalityService.getById(PersonalityId("not-found")), Timeout(2.seconds)) { personality =>
        personality should be(None)
      }
    }
  }

  feature("search personalities") {
    scenario("search all") {
      val futurePersonality = for {
        _ <- persistentPersonalityService.persist(personality.copy(personalityId = PersonalityId("personality2")))
        _ <- persistentPersonalityService.persist(personality.copy(personalityId = PersonalityId("personality3")))
        personalities <- persistentPersonalityService.find(
          start = 0,
          end = None,
          sort = None,
          order = None,
          userId = None,
          questionId = None,
          personalityRole = None
        )
      } yield personalities

      whenReady(futurePersonality, Timeout(2.seconds)) { personalities =>
        personalities.map(_.personalityId) should contain(PersonalityId("personality2"))
      }
    }

    scenario("search by questionId") {
      val futurePersonality = for {
        _ <- persistentQuestionService.persist(question.copy(questionId = QuestionId("question2"), slug = "question-2"))
        _ <- persistentPersonalityService.persist(
          personality.copy(personalityId = PersonalityId("personality4"), questionId = QuestionId("question2"))
        )
        _ <- persistentPersonalityService.persist(
          personality.copy(personalityId = PersonalityId("personality5"), questionId = QuestionId("question2"))
        )
        personalities <- persistentPersonalityService.find(
          start = 0,
          end = None,
          sort = None,
          order = None,
          userId = None,
          questionId = Some(QuestionId("question2")),
          personalityRole = None
        )
      } yield personalities

      whenReady(futurePersonality, Timeout(2.seconds)) { personalities =>
        personalities.map(_.personalityId) should contain(PersonalityId("personality4"))
      }
    }

  }

  feature("count personalities") {
    scenario("count by questionId") {

      val futurePersonalityCount = for {
        _ <- persistentQuestionService.persist(
          question.copy(
            questionId = QuestionId("question-for-count-personality-scenario"),
            slug = "question-count-personality"
          )
        )
        _ <- persistentPersonalityService.persist(
          personality.copy(
            personalityId = PersonalityId("personality-count-1"),
            questionId = QuestionId("question-for-count-personality-scenario")
          )
        )
        _ <- persistentPersonalityService.persist(
          personality.copy(
            personalityId = PersonalityId("personality-count-2"),
            questionId = QuestionId("question-for-count-personality-scenario")
          )
        )
        count <- persistentPersonalityService.count(
          userId = None,
          questionId = Some(QuestionId("question-for-count-personality-scenario")),
          personalityRole = None
        )
      } yield count

      whenReady(futurePersonalityCount, Timeout(2.seconds)) { count =>
        count should be(2)
      }
    }

  }

  feature("update personalities") {
    scenario("update existing personality") {
      val updatedPersonality =
        personality.copy(userId = UserId("updated-user"))

      val updatedUser = TestUtilsIT.user(id = UserId("updated-user"), email = "update-user@make.org")

      val futureUpdatedPersonality = for {
        _           <- persistentUserService.persist(updatedUser)
        personality <- persistentPersonalityService.modify(updatedPersonality)
      } yield personality

      whenReady(futureUpdatedPersonality, Timeout(2.seconds)) { personality =>
        personality.personalityId should be(PersonalityId("personality"))
        personality.userId.value should be("updated-user")
      }
    }
  }

}

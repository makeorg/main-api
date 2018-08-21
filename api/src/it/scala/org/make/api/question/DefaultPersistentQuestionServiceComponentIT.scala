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

import org.make.api.DatabaseTest
import org.make.core.operation.OperationId
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language, ThemeId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DefaultPersistentQuestionServiceComponentIT extends DatabaseTest with DefaultPersistentQuestionServiceComponent {
  override protected val cockroachExposedPort: Int = 40010

  feature("inserting a new question") {

    scenario("insert and then retrieve question") {
      val question = Question(
        questionId = QuestionId("some-question-id"),
        country = Country("FR"),
        language = Language("fr"),
        question = "some question",
        operationId = Some(OperationId("my-operation-id")),
        themeId = Some(ThemeId("my-theme-id"))
      )

      whenReady(persistentQuestionService.getById(questionId = question.questionId), Timeout(2.seconds)) {
        maybeQuestion =>
          maybeQuestion should be(None)
      }

      whenReady(persistentQuestionService.persist(question), Timeout(2.seconds)) { answer =>
        answer should be(question)
      }

      whenReady(persistentQuestionService.getById(questionId = question.questionId), Timeout(2.seconds)) {
        maybeQuestion =>
          maybeQuestion.contains(question) should be(true)
      }
    }

  }
  feature("finding questions") {
    scenario("finding by country and language") {

      val question1 = Question(
        questionId = QuestionId("some-question-id-1"),
        country = Country("AA"),
        language = Language("aa"),
        question = "some question",
        operationId = None,
        themeId = None
      )

      val question2 = Question(
        questionId = QuestionId("some-question-id-2"),
        country = Country("AA"),
        language = Language("bb"),
        question = "some question",
        operationId = None,
        themeId = None
      )

      val question3 = Question(
        questionId = QuestionId("some-question-id-3"),
        country = Country("BB"),
        language = Language("aa"),
        question = "some question",
        operationId = None,
        themeId = None
      )

      val question4 = Question(
        questionId = QuestionId("some-question-id-4"),
        country = Country("BB"),
        language = Language("bb"),
        question = "some question",
        operationId = None,
        themeId = None
      )

      val insertAll: Future[Unit] = for {
        _ <- persistentQuestionService.persist(question1)
        _ <- persistentQuestionService.persist(question2)
        _ <- persistentQuestionService.persist(question3)
        _ <- persistentQuestionService.persist(question4)
      } yield ()

      whenReady(insertAll, Timeout(2.seconds)) { _ =>
        ()
      }

      whenReady(
        persistentQuestionService.find(country = Some(Country("AA")), language = None, operation = None, theme = None),
        Timeout(2.seconds)
      ) { results =>
        results.size should be(2)
        results.exists(_.questionId == question1.questionId) should be(true)
        results.exists(_.questionId == question2.questionId) should be(true)
      }

      whenReady(
        persistentQuestionService.find(country = None, language = Some(Language("aa")), operation = None, theme = None),
        Timeout(2.seconds)
      ) { results =>
        results.size should be(2)
        results.exists(_.questionId == question1.questionId) should be(true)
        results.exists(_.questionId == question3.questionId) should be(true)
      }

      whenReady(
        persistentQuestionService.find(country = None, language = Some(Language("cc")), operation = None, theme = None),
        Timeout(2.seconds)
      ) { results =>
        results.size should be(0)
      }

    }
  }
}

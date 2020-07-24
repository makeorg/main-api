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
import org.make.core.reference.{Country, Language}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DefaultPersistentQuestionServiceComponentIT extends DatabaseTest with DefaultPersistentQuestionServiceComponent {
  override protected val cockroachExposedPort: Int = 40010

  Feature("inserting a new question") {

    Scenario("insert and then retrieve question") {
      val question = Question(
        questionId = QuestionId("some-question-id"),
        slug = "some-question",
        country = Country("FR"),
        language = Language("fr"),
        question = "some question",
        shortTitle = None,
        operationId = Some(OperationId("my-operation-id"))
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
  Feature("finding questions") {
    Scenario("finding by country and language") {

      val question1 = Question(
        questionId = QuestionId("some-question-id-1"),
        slug = "some-aa-question",
        country = Country("AA"),
        language = Language("aa"),
        question = "some question1",
        shortTitle = None,
        operationId = None
      )

      val question2 = Question(
        questionId = QuestionId("some-question-id-2"),
        slug = "some-aa-question-2",
        country = Country("AA"),
        language = Language("bb"),
        question = "some question2",
        shortTitle = None,
        operationId = None
      )

      val question3 = Question(
        questionId = QuestionId("some-question-id-3"),
        slug = "some-bb-question",
        country = Country("BB"),
        language = Language("aa"),
        question = "some question3",
        shortTitle = None,
        operationId = None
      )

      val question4 = Question(
        questionId = QuestionId("some-question-id-4"),
        slug = "some-bb-question-2",
        country = Country("BB"),
        language = Language("bb"),
        question = "some question4",
        shortTitle = None,
        operationId = None
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
        persistentQuestionService.find(SearchQuestionRequest(country = Some(Country("AA")))),
        Timeout(2.seconds)
      ) { results =>
        results.size should be(2)
        results.exists(_.questionId == question1.questionId) should be(true)
        results.exists(_.questionId == question2.questionId) should be(true)
      }

      whenReady(
        persistentQuestionService.find(SearchQuestionRequest(language = Some(Language("aa")))),
        Timeout(2.seconds)
      ) { results =>
        results.size should be(2)
        results.exists(_.questionId == question1.questionId) should be(true)
        results.exists(_.questionId == question3.questionId) should be(true)
      }

      whenReady(
        persistentQuestionService.find(SearchQuestionRequest(language = Some(Language("cc")))),
        Timeout(2.seconds)
      ) { results =>
        results.size should be(0)
      }

      whenReady(persistentQuestionService.find(SearchQuestionRequest(limit = Some(2))), Timeout(2.seconds)) { results =>
        results.size should be(2)
      }

      whenReady(
        persistentQuestionService.find(
          SearchQuestionRequest(limit = Some(3), order = Some("DESC"), sort = Some("slug"), skip = Some(1))
        ),
        Timeout(2.seconds)
      ) { results =>
        results.size should be(3)
        results.head.question should be("some question4")
        results(1).question should be("some question3")
        results(2).question should be("some question2")
      }

      whenReady(
        persistentQuestionService.find(
          SearchQuestionRequest(limit = Some(3), order = Some("ASC"), sort = Some("slug"), skip = Some(2))
        ),
        Timeout(2.seconds)
      ) { results =>
        results.size should be(3)
        results(2).question should be("some question")
        results(1).question should be("some question4")
        results.head.question should be("some question3")
      }

    }

    Scenario("finding by slug") {

      val question1 = Question(
        questionId = QuestionId("some-new-question-id-1"),
        slug = "some-new-aa-question",
        country = Country("AA"),
        language = Language("aa"),
        question = "some question",
        shortTitle = None,
        operationId = None
      )

      val question2 = Question(
        questionId = QuestionId("some-new-question-id-2"),
        slug = "some-new-aa-question",
        country = Country("AA"),
        language = Language("bb"),
        question = "some question",
        shortTitle = None,
        operationId = None
      )

      whenReady(persistentQuestionService.persist(question1), Timeout(5.seconds)) { _ =>
        ()
      }

      whenReady(
        persistentQuestionService.find(SearchQuestionRequest(maybeSlug = Some(question1.slug))),
        Timeout(5.seconds)
      ) { maybeQuestion =>
        maybeQuestion.contains(question1) should be(true)
      }

      whenReady(
        persistentQuestionService.find(SearchQuestionRequest(maybeSlug = Some("some-fake-slug-that-doesn't-exist"))),
        Timeout(5.seconds)
      ) { maybeQuestion =>
        maybeQuestion should be(Seq.empty)
      }

      // Ensure unicity on slug
      whenReady(persistentQuestionService.persist(question2).failed, Timeout(5.seconds)) { _ =>
        ()
      }

    }

    Scenario("finding by slug or id") {
      val question1 = Question(
        questionId = QuestionId("question-slugorid-id-1"),
        slug = "question-slugorid-slug-1",
        country = Country("AA"),
        language = Language("aa"),
        question = "some question 1",
        shortTitle = None,
        operationId = None
      )
      val question2 = Question(
        questionId = QuestionId("question-slugorid-id-2"),
        slug = "question-slugorid-slug-2",
        country = Country("AA"),
        language = Language("bb"),
        question = "some question 2",
        shortTitle = None,
        operationId = None
      )

      val insertQuestions: Future[Unit] = for {
        _ <- persistentQuestionService.persist(question1)
        _ <- persistentQuestionService.persist(question2)
      } yield ()

      whenReady(insertQuestions, Timeout(2.seconds)) { _ =>
        ()
      }

      whenReady(
        persistentQuestionService.getByQuestionIdValueOrSlug(questionIdValueOrSlug = "question-slugorid-id-1"),
        Timeout(5.seconds)
      ) { maybeQuestion =>
        maybeQuestion should be(Some(question1))
      }

      whenReady(
        persistentQuestionService.getByQuestionIdValueOrSlug(questionIdValueOrSlug = "question-slugorid-slug-2"),
        Timeout(5.seconds)
      ) { maybeQuestion =>
        maybeQuestion should be(Some(question2))
      }
    }
  }

  Feature("update question") {
    Scenario("update question") {
      val questionToUpdate = Question(
        questionId = QuestionId("some-new-question-to-update"),
        slug = "some-new-question-to-update",
        country = Country("FR"),
        language = Language("fr"),
        question = "some question ?",
        shortTitle = None,
        operationId = Some(OperationId("operation-id"))
      )

      whenReady(persistentQuestionService.persist(questionToUpdate), Timeout(5.seconds)) { _ =>
        ()
      }

      whenReady(
        persistentQuestionService.find(SearchQuestionRequest(maybeSlug = Some(questionToUpdate.slug))),
        Timeout(5.seconds)
      ) { maybeQuestion =>
        maybeQuestion.contains(questionToUpdate) should be(true)
      }

      whenReady(
        persistentQuestionService.modify(
          questionToUpdate.copy(
            slug = "question-updated",
            country = Country("GB"),
            language = Language("en"),
            question = "new question ?",
            shortTitle = Some("new short title")
          )
        ),
        Timeout(5.seconds)
      ) { question =>
        question.questionId should be(QuestionId("some-new-question-to-update"))
        question.slug should be("question-updated")
        question.country should be(Country("GB"))
        question.language should be(Language("en"))
        question.question should be("new question ?")
        question.shortTitle should be(Some("new short title"))
        question.operationId should be(Some(OperationId("operation-id")))
      }
    }
  }
}

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
import org.make.api.DatabaseTest
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.core.idea.{Idea, IdeaId, TopIdea, TopIdeaId, TopIdeaScores}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentTopIdeaServiceIT
    extends DatabaseTest
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentTopIdeaServiceComponent
    with DefaultPersistentIdeaServiceComponent {

  override protected val cockroachExposedPort: Int = 40020

  val findTopIdea
    : (Int, Option[Int], Option[String], Option[String], Option[IdeaId], Option[QuestionId], Option[String]) => Future[
      Seq[TopIdea]
    ] =
    persistentTopIdeaService.search

  def persistTopIdea(topIdea: TopIdea): Future[TopIdea] = persistentTopIdeaService.persist(topIdea)

  def waitForCompletion(f: Future[_]): Unit = whenReady(f, Timeout(5.seconds))(_ => ())

  def createQuestion(id: QuestionId): Question = Question(
    questionId = id,
    slug = id.value,
    country = Country("FR"),
    language = Language("fr"),
    question = id.value,
    operationId = None,
    themeId = None
  )

  feature("top idea CRUD") {

    scenario("create top idea") {

      val questionId = QuestionId("create-top-idea")

      val insertDependencies = for {
        _ <- persistentQuestionService.persist(createQuestion(questionId))
        _ <- persistentIdeaService.persist(Idea(IdeaId("some-idea"), "test", createdAt = None, updatedAt = None))
        _ <- persistentIdeaService.persist(Idea(IdeaId("idea-1"), "test", createdAt = None, updatedAt = None))
      } yield ()

      waitForCompletion(insertDependencies)

      val insert =
        persistTopIdea(
          TopIdea(TopIdeaId("123"), IdeaId("some-idea"), questionId, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)
        )

      whenReady(insert, Timeout(5.seconds)) { _.topIdeaId should be(TopIdeaId("123")) }

      val unknownIdea =
        persistTopIdea(
          TopIdea(TopIdeaId("678"), IdeaId("unknown-idea"), questionId, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)
        )

      waitForCompletion(unknownIdea.failed)
    }

    scenario("update and get") {

      val questionId = QuestionId("update-and-get")

      val insertDependencies = for {
        _ <- persistentQuestionService.persist(createQuestion(questionId))
        _ <- persistentIdeaService.persist(Idea(IdeaId("update-1"), "Update 1", createdAt = None, updatedAt = None))
        _ <- persistentIdeaService.persist(Idea(IdeaId("update-2"), "Update 2", createdAt = None, updatedAt = None))
      } yield ()

      waitForCompletion(insertDependencies)

      val topIdea =
        TopIdea(TopIdeaId("update-1"), IdeaId("update-1"), questionId, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)

      waitForCompletion(persistTopIdea(topIdea))

      whenReady(persistentTopIdeaService.getById(TopIdeaId("update-1")), Timeout(5.seconds)) { maybeTopIdea =>
        maybeTopIdea should contain(topIdea)
      }

      waitForCompletion(persistentTopIdeaService.modify(topIdea.copy(ideaId = IdeaId("update-2"))))

      whenReady(persistentTopIdeaService.getById(TopIdeaId("update-1")), Timeout(5.seconds)) { maybeTopIdea =>
        maybeTopIdea should contain(topIdea.copy(ideaId = IdeaId("update-2")))
      }

    }

    scenario("Finding top ideas") {
      val questionId1 = QuestionId("finding-top-idea-1")
      val questionId2 = QuestionId("finding-top-idea-2")
      val idea1 = IdeaId("find-1")
      val idea2 = IdeaId("find-2")

      val insertDependencies = for {
        _ <- persistentQuestionService.persist(createQuestion(questionId1))
        _ <- persistentQuestionService.persist(createQuestion(questionId2))
        _ <- persistentIdeaService.persist(Idea(idea1, "Find 1", createdAt = None, updatedAt = None))
        _ <- persistentIdeaService.persist(Idea(idea2, "Find 2", createdAt = None, updatedAt = None))
        _ <- persistTopIdea(
          TopIdea(TopIdeaId("find-1"), idea1, questionId1, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)
        )
        _ <- persistTopIdea(
          TopIdea(TopIdeaId("find-2"), idea2, questionId1, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)
        )
        _ <- persistTopIdea(
          TopIdea(TopIdeaId("find-3"), idea1, questionId1, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)
        )
        _ <- persistTopIdea(
          TopIdea(TopIdeaId("find-4"), idea2, questionId2, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)
        )
        _ <- persistTopIdea(
          TopIdea(TopIdeaId("find-5"), idea2, questionId1, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)
        )
        _ <- persistTopIdea(
          TopIdea(TopIdeaId("find-6"), idea1, questionId2, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)
        )
      } yield ()

      waitForCompletion(insertDependencies)

      whenReady(findTopIdea(0, None, None, None, Some(idea2), None, None), Timeout(5.seconds)) { results =>
        results.map(_.topIdeaId.value).sorted should be(Seq("find-2", "find-4", "find-5"))
      }

      whenReady(findTopIdea(0, None, None, None, None, Some(questionId2), None), Timeout(5.seconds)) { results =>
        results.map(_.topIdeaId.value).sorted should be(Seq("find-4", "find-6"))
      }
    }

    scenario("count top ideas") {
      val questionId = QuestionId("count-top-idea-1")
      val idea = IdeaId("count-1")

      val insertDependencies = for {
        _ <- persistentQuestionService.persist(createQuestion(questionId))
        _ <- persistentIdeaService.persist(Idea(idea, "Count 1", createdAt = None, updatedAt = None))
        _ <- persistTopIdea(
          TopIdea(TopIdeaId("count-1"), idea, questionId, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)
        )
        _ <- persistTopIdea(
          TopIdea(TopIdeaId("count-2"), idea, questionId, "top-idea", "label", TopIdeaScores(0, 0, 0), 0)
        )
      } yield ()

      waitForCompletion(insertDependencies)

      whenReady(persistentTopIdeaService.count(None, Some(questionId), None), Timeout(5.seconds)) {
        _ shouldBe 2
      }
    }
  }
}

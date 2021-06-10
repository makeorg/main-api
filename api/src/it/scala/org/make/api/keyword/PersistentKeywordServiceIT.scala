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

package org.make.api.keyword

import org.make.api.DatabaseTest
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.core.keyword._
import org.make.core.question.QuestionId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentKeywordServiceIT
    extends DatabaseTest
    with DefaultPersistentKeywordServiceComponent
    with DefaultPersistentQuestionServiceComponent {

  override protected val cockroachExposedPort: Int = 40025
  val questionId: QuestionId = QuestionId("question-id")
  val otherQuestionId: QuestionId = QuestionId("other-question-id")
  val thirdQuestionId: QuestionId = QuestionId("third-question-id")

  override def beforeAll(): Unit = {
    super.beforeAll()
    whenReady(persistentQuestionService.persist(question(id = questionId, slug = "kw-1")), Timeout(2.seconds))(_ => ())
    whenReady(persistentQuestionService.persist(question(id = otherQuestionId, slug = "kw-2")), Timeout(2.seconds))(
      _ => ()
    )
    whenReady(persistentQuestionService.persist(question(id = thirdQuestionId, slug = "kw-3")), Timeout(2.seconds))(
      _ => ()
    )
  }

  Feature("Create & Find keywords") {
    Scenario("createKeywords and findTop and findAll") {
      val keywords = Seq(
        keyword(questionId, "foo", score = 6f, topKeyword = true),
        keyword(questionId, "bar", score = 5f, topKeyword = true),
        keyword(questionId, "baz", score = 4f, topKeyword = true),
        keyword(questionId, "qux", score = 3f, topKeyword = true),
        keyword(questionId, "quux", score = 2f, topKeyword = false),
        keyword(otherQuestionId, "quuz", score = 1f, topKeyword = true)
      )

      val futureTopKeywords: Future[Seq[Keyword]] = for {
        _   <- persistentKeywordService.createKeywords(questionId, keywords)
        top <- persistentKeywordService.findTop(questionId, 3)
      } yield top

      whenReady(futureTopKeywords, Timeout(3.seconds)) { result =>
        result.size shouldBe 3
        result.foreach(_.questionId shouldBe questionId)
        result.map(_.key) shouldBe Seq("foo", "bar", "baz")
      }

      whenReady(persistentKeywordService.findAll(questionId), Timeout(3.seconds)) { result =>
        result.size shouldBe 5
        result.foreach(_.questionId shouldBe questionId)
        result.map(_.key) shouldBe Seq("bar", "baz", "foo", "quux", "qux")
      }

      whenReady(persistentKeywordService.get("foo", questionId), Timeout(3.seconds)) { result =>
        result shouldBe defined
        result.foreach(_.key shouldBe "foo")
      }
    }
  }

  Feature("Reset & update top keywords") {
    Scenario("resetTop and updateTop") {
      val keywords = Seq(
        keyword(thirdQuestionId, "toto", score = 3f, topKeyword = true),
        keyword(thirdQuestionId, "tata", score = 2f, topKeyword = true),
        keyword(thirdQuestionId, "tutu", score = 1f, topKeyword = true)
      )

      val update = Seq(keyword(thirdQuestionId, "tata", score = 42f, topKeyword = true))
      val newTop = Seq(
        keyword(thirdQuestionId, "titi", score = 21f, topKeyword = true),
        keyword(thirdQuestionId, "tete", score = 14f, topKeyword = true)
      )

      val futureKeywords: Future[Seq[Keyword]] = for {
        _ <- persistentKeywordService.createKeywords(thirdQuestionId, keywords)
        _ <- persistentKeywordService.createKeywords(
          questionId,
          Seq(keyword(questionId, "check-top", topKeyword = true), keyword(questionId, "tata", topKeyword = true))
        )
        _   <- persistentKeywordService.resetTop(thirdQuestionId)
        _   <- persistentKeywordService.updateTop(thirdQuestionId, update)
        _   <- persistentKeywordService.createKeywords(thirdQuestionId, newTop)
        all <- persistentKeywordService.findAll(thirdQuestionId)
      } yield all

      whenReady(futureKeywords, Timeout(3.seconds)) { result =>
        result.size shouldBe 5
        result.find(_.key == "tata").map(_.score) shouldBe Some(42f)
        result.find(_.key == "tutu").map(_.topKeyword) shouldBe Some(false)
        result.find(_.key == "titi").map(_.topKeyword) shouldBe Some(true)
      }

      whenReady(persistentKeywordService.findAll(questionId), Timeout(3.seconds)) { result =>
        result.find(_.key == "check-top").map(_.topKeyword) shouldBe Some(true)
      }
    }
  }
}

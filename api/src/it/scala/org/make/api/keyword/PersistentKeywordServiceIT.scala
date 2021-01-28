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

  override def beforeAll(): Unit = {
    super.beforeAll()
    whenReady(persistentQuestionService.persist(question(id = questionId, slug = "kw-1")), Timeout(2.seconds))(_ => ())
    whenReady(persistentQuestionService.persist(question(id = otherQuestionId, slug = "kw-2")), Timeout(2.seconds))(
      _ => ()
    )
  }

  Feature("Replace & find all") {
    Scenario("replace and find all") {
      val keywords = Seq(
        keyword(questionId, "foo"),
        keyword(questionId, "bar"),
        keyword(questionId, "baz"),
        keyword(otherQuestionId, "qux")
      )

      val futureKeyword: Future[Seq[Keyword]] = for {
        _   <- persistentKeywordService.replaceAll(questionId, keywords)
        all <- persistentKeywordService.findAll(questionId, 2)
      } yield all

      whenReady(futureKeyword, Timeout(3.seconds)) { result =>
        result.size shouldBe 2
        result.foreach(_.questionId shouldBe questionId)
      }
    }
  }
}

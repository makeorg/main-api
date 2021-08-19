/*
 *  Make.org Core API
 *  Copyright (C) 2021 Make.org
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

package org.make.api.widget

import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.api.{DatabaseTest, TestUtilsIT}
import org.make.core.DateHelper
import org.make.core.question.QuestionId
import org.make.core.reference.Country
import org.make.core.user.UserId
import org.make.core.widget.{Source, SourceId, Widget, WidgetId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.time.ZoneId
import scala.concurrent.duration.DurationInt

class PersistentWidgetServiceIT
    extends DatabaseTest
    with DefaultPersistentQuestionServiceComponent
    with DefaultPersistentSourceServiceComponent
    with DefaultPersistentUserServiceComponent
    with DefaultPersistentWidgetServiceComponent {

  override def beforeAll(): Unit = {
    super.beforeAll()
    whenReady(persistentQuestionService.persist(question(QuestionId("question"))), Timeout(2.seconds)) { _ =>
      ()
    }
    whenReady(persistentUserService.persist(TestUtilsIT.user(UserId("123"))), Timeout(2.seconds)) { _ =>
      ()
    }
    whenReady(persistentSourceService.persist(source), Timeout(2.seconds)) { _ =>
      ()
    }
  }

  private val source =
    Source(
      id = SourceId("source"),
      name = "name",
      source = "source",
      DateHelper.now().withZoneSameInstant(ZoneId.systemDefault()),
      DateHelper.now().withZoneSameInstant(ZoneId.systemDefault()),
      UserId("123")
    )

  private val widget = Widget(
    id = WidgetId("widget"),
    sourceId = SourceId("source"),
    questionId = QuestionId("question"),
    country = Country("FR"),
    author = UserId("123"),
    version = Widget.Version.V1,
    script = "",
    createdAt = DateHelper.now().withZoneSameInstant(ZoneId.systemDefault())
  )

  Feature("CRUD widget") {

    Scenario("persist") {
      whenReady(persistentWidgetService.persist(widget), Timeout(2.seconds)) {
        _ should be(widget)
      }
    }

    Scenario("get") {
      whenReady(persistentWidgetService.get(widget.id), Timeout(2.seconds)) {
        _ shouldBe Some(widget)
      }
    }

    Scenario("list") {
      whenReady(persistentWidgetService.list(source.id, None, None, None, None)) {
        _ shouldBe Seq(widget)
      }
    }

    Scenario("count") {
      whenReady(persistentWidgetService.count(source.id)) {
        _ shouldBe 1
      }
    }

  }

}

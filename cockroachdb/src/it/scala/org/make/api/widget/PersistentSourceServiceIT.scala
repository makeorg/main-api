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

import org.make.api.{DatabaseTest, TestUtils}
import org.make.api.user.DefaultPersistentUserServiceComponent
import org.make.core.DateHelper
import org.make.core.user.UserId
import org.make.core.widget.{Source, SourceId}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.time.ZoneId
import scala.concurrent.duration.DurationInt

class PersistentSourceServiceIT
    extends DatabaseTest
    with DefaultPersistentSourceServiceComponent
    with DefaultPersistentUserServiceComponent {

  override def beforeAll(): Unit = {
    super.beforeAll()
    whenReady(persistentUserService.persist(TestUtils.user(UserId("123"))), Timeout(2.seconds)) { _ =>
      ()
    }
  }

  private val source =
    Source(
      id = SourceId("id"),
      name = "name",
      source = "source",
      DateHelper.now().withZoneSameInstant(ZoneId.systemDefault()),
      DateHelper.now().withZoneSameInstant(ZoneId.systemDefault()),
      UserId("123")
    )

  Feature("CRUD source") {

    Scenario("persist") {
      whenReady(persistentSourceService.persist(source), Timeout(2.seconds)) {
        _ should be(source)
      }
    }

    Scenario("get") {
      whenReady(persistentSourceService.get(source.id), Timeout(2.seconds)) {
        _ shouldBe Some(source)
      }
    }

    Scenario("modify") {

      val original = Source(
        id = SourceId("toUpdate-1"),
        name = "foo",
        source = "bar",
        DateHelper.now(),
        DateHelper.now(),
        UserId("123")
      )

      val updated = original.copy(name = "baz", source = "buz")

      val steps = for {
        _      <- persistentSourceService.persist(original)
        result <- persistentSourceService.modify(updated)
      } yield result

      whenReady(steps, Timeout(2.seconds)) {
        _ shouldBe updated
      }

    }

    Scenario("find by source") {

      whenReady(persistentSourceService.findBySource("sour"), Timeout(2.seconds)) {
        _ shouldBe None
      }

      whenReady(persistentSourceService.findBySource("source"), Timeout(2.seconds)) {
        _ shouldBe Some(source)
      }

    }

    Scenario("list without filters") {
      whenReady(persistentSourceService.list(None, None, None, None, None, None)) {
        _.size shouldBe 2
      }
    }

    Scenario("list with filters") {
      whenReady(persistentSourceService.list(None, None, None, None, Some("nam"), Some("our"))) {
        _ shouldBe Seq(source)
      }
    }

    Scenario("count without filters") {
      whenReady(persistentSourceService.count(None, None)) {
        _ shouldBe 2
      }
    }

    Scenario("count with filters") {
      whenReady(persistentSourceService.count(Some("nam"), Some("our"))) {
        _ shouldBe 1
      }
    }

  }

}

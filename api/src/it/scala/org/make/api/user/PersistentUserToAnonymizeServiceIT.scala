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

package org.make.api.user

import org.make.api.DatabaseTest
import org.make.api.technical.DefaultIdGeneratorComponent
import org.postgresql.util.PSQLException
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt

class PersistentUserToAnonymizeServiceIT
    extends DatabaseTest
    with DefaultPersistentUserToAnonymizeServiceComponent
    with DefaultIdGeneratorComponent {

  override protected val cockroachExposedPort: Int = 40014

  feature("create") {
    scenario("create an entry") {
      whenReady(persistentUserToAnonymizeService.create("toto-mail"), Timeout(3.seconds)) { result =>
        noException shouldBe thrownBy(result)
      }
    }

    scenario("create an already existing entry") {
      val futureCreate: Future[String] =
        for {
          _ <- persistentUserToAnonymizeService.create("tata-mail")
          _ <- persistentUserToAnonymizeService.create("tata-mail")
        } yield "tata-mail"
      intercept[PSQLException] {
        Await.result(futureCreate, 3.seconds)
      }
    }
  }

  feature("find all") {
    scenario("get all mailsPSQLException ordered by date") {
      whenReady(persistentUserToAnonymizeService.create("toto"), Timeout(3.seconds))(_ => ())
      whenReady(persistentUserToAnonymizeService.create("tata"), Timeout(3.seconds))(_ => ())
      whenReady(persistentUserToAnonymizeService.create("titi"), Timeout(3.seconds))(_ => ())
      whenReady(persistentUserToAnonymizeService.create("tutu"), Timeout(3.seconds))(_ => ())

      whenReady(persistentUserToAnonymizeService.findAll(), Timeout(3.seconds)) { result =>
        result.contains("toto") shouldBe true
        result.contains("tata") shouldBe true
        result.contains("titi") shouldBe true
        result.contains("tutu") shouldBe true
      }
    }
  }

  feature("delete") {
    scenario("delete by email") {
      whenReady(persistentUserToAnonymizeService.create("delete-me"), Timeout(3.seconds))(_     => ())
      whenReady(persistentUserToAnonymizeService.create("delete-me-too"), Timeout(3.seconds))(_ => ())
      whenReady(persistentUserToAnonymizeService.create("keep-me"), Timeout(3.seconds))(_       => ())
      whenReady(persistentUserToAnonymizeService.findAll(), Timeout(3.seconds)) { result =>
        result.contains("delete-me") shouldBe true
        result.contains("delete-me-too") shouldBe true
        result.contains("keep-me") shouldBe true
      }

      whenReady(
        persistentUserToAnonymizeService.removeAllByEmails(Seq("delete-me", "delete-me-too")),
        Timeout(3.seconds)
      )(_ => ())
      whenReady(persistentUserToAnonymizeService.findAll(), Timeout(3.seconds)) { result =>
        result.contains("delete-me") shouldBe false
        result.contains("delete-me-too") shouldBe false
        result.contains("keep-me") shouldBe true
      }
    }

    scenario("delete all") {
      whenReady(persistentUserToAnonymizeService.create("delete-me-1"), Timeout(3.seconds))(_ => ())
      whenReady(persistentUserToAnonymizeService.create("delete-me-2"), Timeout(3.seconds))(_ => ())
      whenReady(persistentUserToAnonymizeService.create("delete-me-3"), Timeout(3.seconds))(_ => ())
      whenReady(persistentUserToAnonymizeService.findAll(), Timeout(3.seconds)) { result =>
        result.contains("delete-me-1") shouldBe true
        result.contains("delete-me-2") shouldBe true
        result.contains("delete-me-3") shouldBe true
      }

      whenReady(persistentUserToAnonymizeService.removeAll(), Timeout(3.seconds))(_ => ())
      whenReady(persistentUserToAnonymizeService.findAll(), Timeout(3.seconds)) { result =>
        result.contains("delete-me-1") shouldBe false
        result.contains("delete-me-2") shouldBe false
        result.contains("delete-me-3") shouldBe false
      }

    }
  }

}

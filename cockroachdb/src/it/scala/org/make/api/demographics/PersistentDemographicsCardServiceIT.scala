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

package org.make.api.demographics

import org.make.api.{DatabaseTest, TestUtils}
import org.make.core.DateHelper
import org.make.core.demographics.DemographicsCard.Layout
import org.make.core.demographics.{DemographicsCard, DemographicsCardId}
import org.make.core.reference.Language
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import java.time.ZoneId
import scala.concurrent.duration.DurationInt

class PersistentDemographicsCardServiceIT extends DatabaseTest with DefaultPersistentDemographicsCardServiceComponent {

  private val card = TestUtils.demographicsCard(DemographicsCardId("id"))

  Feature("CRUD demographicsCard") {

    Scenario("persist") {
      whenReady(persistentDemographicsCardService.persist(card), Timeout(2.seconds)) {
        _ should be(card)
      }
    }

    Scenario("get") {
      whenReady(persistentDemographicsCardService.get(card.id), Timeout(2.seconds)) {
        _ shouldBe Some(card)
      }
    }

    Scenario("modify") {

      val original = DemographicsCard(
        id = DemographicsCardId("id-update"),
        name = "Demo name",
        layout = Layout.ThreeColumnsRadio,
        dataType = "data-type",
        language = Language("fr"),
        title = "Demo title",
        parameters = """[{"label":"Option update", "value":"value-update"}]""",
        createdAt = DateHelper.now().withZoneSameInstant(ZoneId.systemDefault()),
        updatedAt = DateHelper.now().withZoneSameInstant(ZoneId.systemDefault())
      )

      val updated = original.copy(
        name = "new name",
        layout = Layout.OneColumnRadio,
        dataType = "new-data-type",
        language = Language("es"),
        title = "new title",
        parameters = """[{"label":"new option", "value":"new-value"}]""",
        updatedAt = DateHelper.now().withZoneSameInstant(ZoneId.systemDefault())
      )

      val steps = for {
        _      <- persistentDemographicsCardService.persist(original)
        result <- persistentDemographicsCardService.modify(updated)
      } yield result

      whenReady(steps, Timeout(2.seconds)) {
        _ shouldBe updated
      }

    }

    Scenario("list without filters") {
      whenReady(persistentDemographicsCardService.list(None, None, None, None, None, None)) {
        _.size shouldBe 2
      }
    }

    Scenario("list with filters") {
      whenReady(persistentDemographicsCardService.list(None, None, None, None, Some(Language("fr")), Some("data-type"))) {
        _ shouldBe Seq(card)
      }
    }

    Scenario("count without filters") {
      whenReady(persistentDemographicsCardService.count(None, None)) {
        _ shouldBe 2
      }
    }

    Scenario("count with filters") {
      whenReady(persistentDemographicsCardService.count(Some(Language("fr")), Some("data-type"))) {
        _ shouldBe 1
      }
    }

  }

}

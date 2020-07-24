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

package org.make.api.tagtype

import org.make.api.DatabaseTest
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.tag._
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PersistentTagTypeServiceIT
    extends DatabaseTest
    with DefaultPersistentTagTypeServiceComponent
    with DefaultIdGeneratorComponent {

  override protected val cockroachExposedPort: Int = 40009

  def newTagType(label: String): TagType =
    TagType(
      tagTypeId = idGenerator.nextTagTypeId(),
      label = label,
      display = TagTypeDisplay.Displayed,
      requiredForEnrichment = false
    )

  val stark: TagType = newTagType("Stark")

  val targaryen: TagType = newTagType("Targaryen")
  val lannister: TagType = newTagType("Lannister")
  val bolton: TagType = newTagType("Bolton").copy(requiredForEnrichment = true)
  val greyjoy: TagType = newTagType("Greyjoy").copy(requiredForEnrichment = true)

  val tully: TagType = newTagType("Tully")

  Feature("One tagType can be persisted and retrieved") {
    Scenario("Get tagType by tagTypeId") {
      Given(s"""a persisted tagType "${stark.label}"""")
      When(s"""I search the tagType by tagTypeId "${stark.tagTypeId.value}"""")
      val futureTagType: Future[Option[TagType]] = for {
        _            <- persistentTagTypeService.persist(stark)
        tagTypeStark <- persistentTagTypeService.get(stark.tagTypeId)
      } yield tagTypeStark

      whenReady(futureTagType, Timeout(3.seconds)) { result =>
        Then("the tagType label must be Stark")
        result.map(_.label) shouldBe Some("Stark")
      }
    }

    Scenario("Get tagType by tagTypeId that does not exists") {
      Given("""a nonexistent tagType "fake"""")
      When("""I search the tagType from tagTypeId "fake"""")
      val futureTagTypeId: Future[Option[TagType]] = persistentTagTypeService.get(TagTypeId("fake"))

      whenReady(futureTagTypeId, Timeout(3.seconds)) { result =>
        Then("result should be None")
        result shouldBe None
      }
    }
  }

  Feature("A list of tagTypes can be retrieved") {
    Scenario("Get a list of all enabled tagTypes") {
      Given(s"""a list of persisted tagTypes:
               |label = ${targaryen.label}, tagTypeId = ${targaryen.tagTypeId.value}
               |label = ${lannister.label}, tagTypeId = ${lannister.tagTypeId.value}
               |label = ${bolton.label}, tagTypeId = ${bolton.tagTypeId.value}
               |label = ${greyjoy.label}, tagTypeId = ${greyjoy.tagTypeId.value}
        """.stripMargin)
      val futurePersistedTagTypeList: Future[Seq[TagType]] = for {
        _                <- persistentTagTypeService.persist(targaryen)
        tagTypeLannister <- persistentTagTypeService.persist(lannister)
        tagTypeBolton    <- persistentTagTypeService.persist(bolton)
        tagTypeGreyjoy   <- persistentTagTypeService.persist(greyjoy)
      } yield Seq(tagTypeLannister, tagTypeBolton, tagTypeGreyjoy)

      When("""I retrieve the tagTypes list""")
      val futureTagTypesLists: Future[(Seq[TagType], Seq[TagType])] = for {
        persistedTagTypesList <- futurePersistedTagTypeList
        foundTagTypes         <- persistentTagTypeService.findAll()
      } yield foundTagTypes -> persistedTagTypesList

      whenReady(futureTagTypesLists, Timeout(3.seconds)) {
        case (persisted, found) =>
          Then("result should contain a list of tagTypes of targaryen, lannister, bolton and greyjoy.")
          found.forall(persisted.contains) should be(true)
      }

      When("""I retrieve the tagTypes list of requiredForEnrichment""")
      whenReady(persistentTagTypeService.findAll(requiredForEnrichment = Some(true)), Timeout(3.seconds)) { found =>
        Then("result should contain a list of tagTypes of bolton and greyjoy only.")
        found.size shouldBe 2
        found.map(_.tagTypeId).toSet shouldBe Set(bolton.tagTypeId, greyjoy.tagTypeId)
      }
    }
  }

  Feature("One tagType can be updated") {
    Scenario("Update tagType") {
      Given(s"""a persisted tagType "${tully.label}"""")
      When("I update the tagType label and weight")
      val futureTagType: Future[Option[TagType]] = for {
        _            <- persistentTagTypeService.persist(tully)
        tagTypeStark <- persistentTagTypeService.update(tully.copy(label = "new tully", weight = 42))
      } yield tagTypeStark

      whenReady(futureTagType, Timeout(3.seconds)) { result =>
        Then("the tagType label must have changed")
        result.map(_.tagTypeId.value) shouldBe Some(tully.tagTypeId.value)
        result.map(_.label) shouldBe Some("new tully")
        result.map(_.weight) shouldBe Some(42)
      }
    }

    Scenario("Update tagType that does not exists") {
      Given("""a nonexistent tagType "fake"""")
      When("I update the fake tagType")
      val futureTagTypeId: Future[Option[TagType]] = persistentTagTypeService.update(newTagType("fake"))

      whenReady(futureTagTypeId, Timeout(3.seconds)) { result =>
        Then("result should be None")
        result shouldBe None
      }
    }
  }

}

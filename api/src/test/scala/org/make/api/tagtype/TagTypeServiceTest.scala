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

import org.make.api.MakeUnitTest
import org.make.api.technical.DefaultIdGeneratorComponent
import org.make.core.tag._
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class TagTypeServiceTest
    extends MakeUnitTest
    with DefaultTagTypeServiceComponent
    with PersistentTagTypeServiceComponent
    with DefaultIdGeneratorComponent {

  override val persistentTagTypeService: PersistentTagTypeService = mock[PersistentTagTypeService]

  feature("get tagType") {
    scenario("get tagType from TagTypeId") {
      tagTypeService.getTagType(TagTypeId("valid-tagType"))

      Mockito.verify(persistentTagTypeService).get(TagTypeId("valid-tagType"))
    }
  }

  feature("create tagType") {
    scenario("creating a tagType success") {
      Mockito
        .when(persistentTagTypeService.get(ArgumentMatchers.any[TagTypeId]))
        .thenReturn(Future.successful(None))

      val tagType =
        TagType(
          tagTypeId = TagTypeId("new-tagType"),
          label = "new tagType",
          display = TagTypeDisplay.Displayed,
          requiredForEnrichment = false
        )

      Mockito
        .when(persistentTagTypeService.persist(ArgumentMatchers.any[TagType]))
        .thenReturn(Future.successful(tagType))

      val futureNewTagType: Future[TagType] =
        tagTypeService.createTagType("new tagType", TagTypeDisplay.Displayed, 0, requiredForEnrichment = false)

      whenReady(futureNewTagType, Timeout(3.seconds)) { _ =>
        Mockito.verify(persistentTagTypeService).persist(ArgumentMatchers.any[TagType])
      }
    }
  }

  feature("find tagTypes") {
    scenario("find all tagTypes") {
      tagTypeService.findAll()

      Mockito.verify(persistentTagTypeService).findAll(ArgumentMatchers.eq(None))

      tagTypeService.findAll(Some(true))
      Mockito.verify(persistentTagTypeService).findAll(ArgumentMatchers.eq(Some(true)))
    }

    scenario("find tagTypes with ids 'find-tagType1' and 'find-tagType2'") {
      val tagType1 =
        TagType(
          tagTypeId = TagTypeId("find-tatType-1"),
          label = "TagType 1",
          display = TagTypeDisplay.Displayed,
          requiredForEnrichment = false
        )
      val tagType2 =
        TagType(
          tagTypeId = TagTypeId("find-tatType-2"),
          label = "TagType 2",
          display = TagTypeDisplay.Displayed,
          requiredForEnrichment = false
        )
      Mockito.reset(persistentTagTypeService)
      Mockito
        .when(persistentTagTypeService.findAllFromIds(ArgumentMatchers.any[Seq[TagTypeId]]))
        .thenReturn(Future.successful(Seq(tagType1, tagType2)))

      val futureTagTypes: Future[Seq[TagType]] =
        tagTypeService.findByTagTypeIds(Seq(tagType1.tagTypeId, tagType2.tagTypeId))

      whenReady(futureTagTypes, Timeout(3.seconds)) { tagTypes =>
        tagTypes.size shouldBe 2
        tagTypes.map(_.label).contains(tagType1.label) shouldBe true
        tagTypes.map(_.label).contains(tagType2.label) shouldBe true
      }
    }
  }

  feature("update a tagType") {
    scenario("update a tagType") {
      val oldTagType =
        TagType(TagTypeId("1234567890"), "old TagType", TagTypeDisplay.Hidden, requiredForEnrichment = false)
      val newTagType =
        TagType(TagTypeId("1234567890"), "new TagType", TagTypeDisplay.Displayed, requiredForEnrichment = true)
      Mockito
        .when(persistentTagTypeService.get(TagTypeId("1234567890")))
        .thenReturn(Future.successful(Some(oldTagType)))
      Mockito
        .when(persistentTagTypeService.update(ArgumentMatchers.any[TagType]))
        .thenReturn(Future.successful(Some(newTagType)))

      val futureTagType: Future[Option[TagType]] = tagTypeService.updateTagType(
        tagTypeId = oldTagType.tagTypeId,
        newTagTypeLabel = "new tagType",
        newTagTypeDisplay = TagTypeDisplay.Displayed,
        newTagTypeWeight = 42,
        requiredForEnrichment = true
      )

      whenReady(futureTagType, Timeout(3.seconds)) { tagType =>
        tagType.map(_.label) shouldEqual Some(newTagType.label)
      }
    }

    scenario("update an non existent tagType ") {
      Mockito.when(persistentTagTypeService.get(TagTypeId("non-existent-tagType"))).thenReturn(Future.successful(None))

      val futureTagType: Future[Option[TagType]] = tagTypeService.updateTagType(
        tagTypeId = TagTypeId("non-existent-tagType"),
        newTagTypeLabel = "new non existent tagType",
        newTagTypeDisplay = TagTypeDisplay.Displayed,
        newTagTypeWeight = 42,
        requiredForEnrichment = false
      )

      whenReady(futureTagType) { tagType =>
        tagType shouldBe empty
      }
    }
  }

}

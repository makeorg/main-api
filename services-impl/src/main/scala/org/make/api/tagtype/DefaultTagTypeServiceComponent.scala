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

import org.make.api.technical.IdGeneratorComponent
import org.make.core.tag._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DefaultTagTypeServiceComponent extends TagTypeServiceComponent {
  this: PersistentTagTypeServiceComponent with IdGeneratorComponent =>

  override lazy val tagTypeService: TagTypeService = new DefaultTagTypeService

  class DefaultTagTypeService extends TagTypeService {

    override def getTagType(slug: TagTypeId): Future[Option[TagType]] = {
      persistentTagTypeService.get(slug)
    }

    override def createTagType(
      label: String,
      display: TagTypeDisplay,
      weight: Int,
      requiredForEnrichment: Boolean
    ): Future[TagType] = {
      persistentTagTypeService.persist(
        TagType(
          tagTypeId = idGenerator.nextTagTypeId(),
          label = label,
          display = display,
          weight = weight,
          requiredForEnrichment = requiredForEnrichment
        )
      )
    }

    override def findAll(requiredForEnrichmentFilter: Option[Boolean] = None): Future[Seq[TagType]] = {
      persistentTagTypeService.findAll(requiredForEnrichmentFilter)
    }

    override def findByTagTypeIds(tagIds: Seq[TagTypeId]): Future[Seq[TagType]] = {
      persistentTagTypeService.findAllFromIds(tagIds)
    }

    override def updateTagType(
      tagTypeId: TagTypeId,
      newTagTypeLabel: String,
      newTagTypeDisplay: TagTypeDisplay,
      newTagTypeWeight: Int,
      requiredForEnrichment: Boolean
    ): Future[Option[TagType]] = {
      getTagType(tagTypeId).flatMap {
        case Some(tagType) =>
          persistentTagTypeService
            .update(
              tagType.copy(
                label = newTagTypeLabel,
                display = newTagTypeDisplay,
                weight = newTagTypeWeight,
                requiredForEnrichment = requiredForEnrichment
              )
            )
        case None => Future.successful(None)
      }
    }
  }
}

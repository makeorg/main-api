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

import org.make.core.tag._

import scala.concurrent.Future

trait TagTypeServiceComponent {
  def tagTypeService: TagTypeService
}

trait TagTypeService {
  def getTagType(tagTypeId: TagTypeId): Future[Option[TagType]]
  def createTagType(
    label: String,
    display: TagTypeDisplay,
    weight: Int,
    requiredForEnrichment: Boolean
  ): Future[TagType]
  def findAll(requiredForEnrichmentFilter: Option[Boolean] = None): Future[Seq[TagType]]
  def findByTagTypeIds(tagIds: Seq[TagTypeId]): Future[Seq[TagType]]
  def updateTagType(
    tagTypeId: TagTypeId,
    newTagTypeLabel: String,
    newTagTypeDisplay: TagTypeDisplay,
    newTagTypeWeight: Int,
    requiredForEnrichment: Boolean
  ): Future[Option[TagType]]
}

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

package org.make.api.personality

import org.make.core.personality.{FieldType, PersonalityRoleField, PersonalityRoleFieldId, PersonalityRoleId}
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersonalityRoleFieldServiceComponent {
  def personalityRoleFieldService: PersonalityRoleFieldService
}

trait PersonalityRoleFieldService {
  def getPersonalityRoleField(
    personalityRoleFieldId: PersonalityRoleFieldId,
    personalityRoleId: PersonalityRoleId
  ): Future[Option[PersonalityRoleField]]
  def find(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    personalityRoleId: Option[PersonalityRoleId],
    name: Option[String],
    fieldType: Option[FieldType],
    required: Option[Boolean]
  ): Future[Seq[PersonalityRoleField]]
  def count(
    personalityRoleId: Option[PersonalityRoleId],
    name: Option[String],
    fieldType: Option[FieldType],
    required: Option[Boolean]
  ): Future[Int]
  def createPersonalityRoleField(
    personalityRoleId: PersonalityRoleId,
    request: CreatePersonalityRoleFieldRequest
  ): Future[PersonalityRoleField]
  def updatePersonalityRoleField(
    personalityRoleFieldId: PersonalityRoleFieldId,
    personalityRoleId: PersonalityRoleId,
    request: UpdatePersonalityRoleFieldRequest
  ): Future[Option[PersonalityRoleField]]
  def deletePersonalityRoleField(personalityRoleFieldId: PersonalityRoleFieldId): Future[Unit]
}

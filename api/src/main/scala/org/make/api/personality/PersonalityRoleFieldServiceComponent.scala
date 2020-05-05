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

import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.core.personality.{FieldType, PersonalityRoleField, PersonalityRoleFieldId, PersonalityRoleId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PersonalityRoleFieldServiceComponent {
  def personalityRoleFieldService: PersonalityRoleFieldService
}

trait PersonalityRoleFieldService extends ShortenedNames {
  def getPersonalityRoleField(
    personalityRoleFieldId: PersonalityRoleFieldId,
    personalityRoleId: PersonalityRoleId
  ): Future[Option[PersonalityRoleField]]
  def find(
    start: Int,
    end: Option[Int],
    sort: Option[String],
    order: Option[String],
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

trait DefaultPersonalityRoleFieldServiceComponent extends PersonalityRoleFieldServiceComponent {
  this: PersistentPersonalityRoleFieldServiceComponent with IdGeneratorComponent =>

  override lazy val personalityRoleFieldService: DefaultPersonalityRoleFieldService =
    new DefaultPersonalityRoleFieldService

  class DefaultPersonalityRoleFieldService extends PersonalityRoleFieldService {

    override def getPersonalityRoleField(
      personalityRoleFieldId: PersonalityRoleFieldId,
      personalityRoleId: PersonalityRoleId
    ): Future[Option[PersonalityRoleField]] = {
      persistentPersonalityRoleFieldService.getById(personalityRoleFieldId, personalityRoleId)
    }

    override def createPersonalityRoleField(
      personalityRoleId: PersonalityRoleId,
      request: CreatePersonalityRoleFieldRequest
    ): Future[PersonalityRoleField] = {
      val personalityRoleField: PersonalityRoleField =
        PersonalityRoleField(
          personalityRoleFieldId = idGenerator.nextPersonalityRoleFieldId(),
          personalityRoleId = personalityRoleId,
          name = request.name,
          fieldType = request.fieldType,
          required = request.required
        )
      persistentPersonalityRoleFieldService.persist(personalityRoleField)
    }

    override def updatePersonalityRoleField(
      personalityRoleFieldId: PersonalityRoleFieldId,
      personalityRoleId: PersonalityRoleId,
      request: UpdatePersonalityRoleFieldRequest
    ): Future[Option[PersonalityRoleField]] = {
      persistentPersonalityRoleFieldService.getById(personalityRoleFieldId, personalityRoleId).flatMap {
        case Some(personalityRoleField) =>
          persistentPersonalityRoleFieldService
            .modify(
              personalityRoleField.copy(name = request.name, fieldType = request.fieldType, required = request.required)
            )
            .map(Some.apply)
        case None => Future.successful(None)
      }
    }

    override def find(
      start: Int,
      end: Option[Int],
      sort: Option[String],
      order: Option[String],
      personalityRoleId: Option[PersonalityRoleId],
      name: Option[String],
      fieldType: Option[FieldType],
      required: Option[Boolean]
    ): Future[Seq[PersonalityRoleField]] = {
      persistentPersonalityRoleFieldService.find(start, end, sort, order, personalityRoleId, name, fieldType, required)
    }

    override def count(
      personalityRoleId: Option[PersonalityRoleId],
      name: Option[String],
      fieldType: Option[FieldType],
      required: Option[Boolean]
    ): Future[Int] = {
      persistentPersonalityRoleFieldService.count(personalityRoleId, name, fieldType, required)
    }

    override def deletePersonalityRoleField(personalityRoleFieldId: PersonalityRoleFieldId): Future[Unit] = {
      persistentPersonalityRoleFieldService.delete(personalityRoleFieldId)
    }

  }
}

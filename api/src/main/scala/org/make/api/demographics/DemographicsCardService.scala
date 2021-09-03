/*
 *  Make.org Core API
 *  Copyright (C) 2020 Make.org
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

import cats.implicits._
import org.make.api.technical.IdGeneratorComponent
import org.make.core.demographics.DemographicsCard.Layout
import org.make.core.demographics.{DemographicsCard, DemographicsCardId}
import org.make.core.reference.Language
import org.make.core.technical.Pagination.{End, Start}
import org.make.core.{DateHelperComponent, Order}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DemographicsCardService {
  def get(id: DemographicsCardId): Future[Option[DemographicsCard]]
  def list(
    start: Option[Start],
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    language: Option[Language],
    dataType: Option[String]
  ): Future[Seq[DemographicsCard]]
  def create(
    name: String,
    layout: Layout,
    dataType: String,
    language: Language,
    title: String,
    parameters: String
  ): Future[DemographicsCard]
  def update(
    id: DemographicsCardId,
    name: String,
    layout: Layout,
    dataType: String,
    language: Language,
    title: String,
    parameters: String
  ): Future[Option[DemographicsCard]]
  def count(language: Option[Language], dataType: Option[String]): Future[Int]
}

trait DemographicsCardServiceComponent {
  def demographicsCardService: DemographicsCardService
}

trait DefaultDemographicsCardServiceComponent extends DemographicsCardServiceComponent {
  self: DateHelperComponent with IdGeneratorComponent with PersistentDemographicsCardServiceComponent =>

  override def demographicsCardService: DemographicsCardService = new DemographicsCardService {

    override def get(id: DemographicsCardId): Future[Option[DemographicsCard]] =
      persistentDemographicsCardService.get(id)

    override def list(
      start: Option[Start],
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      language: Option[Language],
      dataType: Option[String]
    ): Future[Seq[DemographicsCard]] =
      persistentDemographicsCardService.list(start, end, sort, order, language, dataType)

    override def create(
      name: String,
      layout: Layout,
      dataType: String,
      language: Language,
      title: String,
      parameters: String
    ): Future[DemographicsCard] =
      persistentDemographicsCardService.persist(
        DemographicsCard(
          id = idGenerator.nextDemographicsCardId(),
          name = name,
          layout = layout,
          dataType = dataType,
          language = language,
          title = title,
          parameters = parameters,
          createdAt = dateHelper.now(),
          updatedAt = dateHelper.now()
        )
      )

    override def update(
      id: DemographicsCardId,
      name: String,
      layout: Layout,
      dataType: String,
      language: Language,
      title: String,
      parameters: String
    ): Future[Option[DemographicsCard]] = {
      get(id).flatMap(
        _.traverse(
          existing =>
            persistentDemographicsCardService
              .modify(
                existing.copy(
                  name = name,
                  layout = layout,
                  dataType = dataType,
                  language = language,
                  title = title,
                  parameters = parameters,
                  updatedAt = dateHelper.now()
                )
              )
        )
      )
    }

    override def count(language: Option[Language], dataType: Option[String]): Future[Int] =
      persistentDemographicsCardService.count(language, dataType)

  }
}

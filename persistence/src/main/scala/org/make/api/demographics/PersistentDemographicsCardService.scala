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

import org.make.core.Order
import org.make.core.demographics.{DemographicsCard, DemographicsCardId}
import org.make.core.reference.Language
import org.make.core.technical.Pagination.{End, Start}

import scala.concurrent.Future

trait PersistentDemographicsCardService {
  def get(id: DemographicsCardId): Future[Option[DemographicsCard]]
  def list(
    start: Option[Start],
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    language: Option[Language],
    dataType: Option[String]
  ): Future[Seq[DemographicsCard]]
  def persist(demographicsCard: DemographicsCard): Future[DemographicsCard]
  def modify(demographicsCard: DemographicsCard): Future[DemographicsCard]
  def count(language: Option[Language], dataType: Option[String]): Future[Int]
}

trait PersistentDemographicsCardServiceComponent {
  def persistentDemographicsCardService: PersistentDemographicsCardService
}

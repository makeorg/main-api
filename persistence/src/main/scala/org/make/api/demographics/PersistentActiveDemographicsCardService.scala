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
import org.make.core.demographics._
import org.make.core.question.QuestionId
import org.make.core.technical.Pagination.{End, Start}

import scala.concurrent.Future

trait PersistentActiveDemographicsCardService {

  def get(id: ActiveDemographicsCardId): Future[Option[ActiveDemographicsCard]]
  def count(questionId: Option[QuestionId], cardId: Option[DemographicsCardId]): Future[Int]
  def list(
    start: Option[Start],
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    questionId: Option[QuestionId],
    cardId: Option[DemographicsCardId]
  ): Future[Seq[ActiveDemographicsCard]]
  def persist(activeDemographicsCard: ActiveDemographicsCard): Future[ActiveDemographicsCard]
  def delete(id: ActiveDemographicsCardId): Future[Unit]
}

trait PersistentActiveDemographicsCardServiceComponent {
  def persistentActiveDemographicsCardService: PersistentActiveDemographicsCardService
}

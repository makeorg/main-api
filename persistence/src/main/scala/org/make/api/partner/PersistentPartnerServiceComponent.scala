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

package org.make.api.partner

import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import org.make.core.Order

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentPartnerServiceComponent {
  def persistentPartnerService: PersistentPartnerService
}

trait PersistentPartnerService {
  def persist(partner: Partner): Future[Partner]
  def modify(partner: Partner): Future[Partner]
  def getById(partnerId: PartnerId): Future[Option[Partner]]
  def find(
    start: Start,
    end: Option[End],
    sort: Option[String],
    order: Option[Order],
    questionId: Option[QuestionId],
    organisationId: Option[UserId],
    partnerKind: Option[PartnerKind]
  ): Future[Seq[Partner]]
  def count(
    questionId: Option[QuestionId],
    organisationId: Option[UserId],
    partnerKind: Option[PartnerKind]
  ): Future[Int]
  def delete(partnerId: PartnerId): Future[Unit]
}

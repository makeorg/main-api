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

import org.make.api.technical.IdGeneratorComponent
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import org.make.core.Order

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait DefaultPartnerServiceComponent extends PartnerServiceComponent {
  this: PersistentPartnerServiceComponent with IdGeneratorComponent =>

  override lazy val partnerService: DefaultPartnerService = new DefaultPartnerService

  class DefaultPartnerService extends PartnerService {

    override def getPartner(partnerId: PartnerId): Future[Option[Partner]] = {
      persistentPartnerService.getById(partnerId)
    }

    override def createPartner(request: CreatePartnerRequest): Future[Partner] = {
      val partner: Partner = Partner(
        partnerId = idGenerator.nextPartnerId(),
        name = request.name,
        logo = request.logo,
        link = request.link,
        organisationId = request.organisationId,
        partnerKind = request.partnerKind,
        questionId = request.questionId,
        weight = request.weight
      )
      persistentPartnerService.persist(partner)
    }

    override def updatePartner(partnerId: PartnerId, request: UpdatePartnerRequest): Future[Option[Partner]] = {
      persistentPartnerService.getById(partnerId).flatMap {
        case Some(partner) =>
          persistentPartnerService
            .modify(
              partner.copy(
                name = request.name,
                logo = request.logo,
                link = request.link,
                organisationId = request.organisationId,
                partnerKind = request.partnerKind,
                weight = request.weight
              )
            )
            .map(Some.apply)
        case None => Future.successful(None)
      }
    }

    override def find(
      start: Start,
      end: Option[End],
      sort: Option[String],
      order: Option[Order],
      questionId: Option[QuestionId],
      organisationId: Option[UserId],
      partnerKind: Option[PartnerKind]
    ): Future[Seq[Partner]] = {
      persistentPartnerService.find(start, end, sort, order, questionId, organisationId, partnerKind)
    }

    override def count(
      questionId: Option[QuestionId],
      organisationId: Option[UserId],
      partnerKind: Option[PartnerKind]
    ): Future[Int] = {
      persistentPartnerService.count(questionId, organisationId, partnerKind)
    }

    override def deletePartner(partnerId: PartnerId): Future[Unit] = {
      persistentPartnerService.delete(partnerId)
    }

  }
}

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

import org.make.api.technical.{IdGeneratorComponent, ShortenedNames}
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.QuestionId
import org.make.core.user.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait PartnerServiceComponent {
  def partnerService: PartnerService
}

trait PartnerService extends ShortenedNames {
  def getPartner(partnerId: PartnerId): Future[Option[Partner]]
  def find(start: Int,
           end: Option[Int],
           sort: Option[String],
           order: Option[String],
           questionId: Option[QuestionId],
           organisationId: Option[UserId],
           partnerKind: Option[PartnerKind]): Future[Seq[Partner]]
  def count(questionId: Option[QuestionId],
            organisationId: Option[UserId],
            partnerKind: Option[PartnerKind]): Future[Int]
  def createPartner(request: CreatePartnerRequest): Future[Partner]
  def updatePartner(partnerId: PartnerId, request: UpdatePartnerRequest): Future[Option[Partner]]
  def deletePartner(partnerId: PartnerId): Future[Unit]
}

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

    override def find(start: Int,
                      end: Option[Int],
                      sort: Option[String],
                      order: Option[String],
                      questionId: Option[QuestionId],
                      organisationId: Option[UserId],
                      partnerKind: Option[PartnerKind]): Future[Seq[Partner]] = {
      persistentPartnerService.find(start, end, sort, order, questionId, organisationId, partnerKind)
    }

    override def count(questionId: Option[QuestionId],
                       organisationId: Option[UserId],
                       partnerKind: Option[PartnerKind]): Future[Int] = {
      persistentPartnerService.count(questionId, organisationId, partnerKind)
    }

    override def deletePartner(partnerId: PartnerId): Future[Unit] = {
      persistentPartnerService.delete(partnerId)
    }

  }
}

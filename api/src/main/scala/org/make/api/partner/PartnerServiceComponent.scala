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
  def createPartner(entity: CreatePartner): Future[Partner]
  def updatePartner(entity: UpdatePartner): Future[Option[Partner]]
  def find(questionId: Option[QuestionId], organisationId: Option[UserId]): Future[Seq[Partner]]
  def count(questionId: Option[QuestionId], organisationId: Option[UserId]): Future[Int]
}

trait DefaultPartnerServiceComponent extends PartnerServiceComponent {
  this: PersistentPartnerServiceComponent with IdGeneratorComponent =>

  val partnerService: DefaultPartnerService = new DefaultPartnerService

  class DefaultPartnerService extends PartnerService {

    override def getPartner(partnerId: PartnerId): Future[Option[Partner]] = {
      persistentPartnerService.getById(partnerId)
    }

    override def createPartner(entity: CreatePartner): Future[Partner] = {
      val partner: Partner = Partner(
        partnerId = idGenerator.nextPartnerId(),
        name = entity.name,
        logo = entity.logo,
        link = entity.link,
        organisationId = entity.organisationId,
        partnerKind = entity.partnerKind,
        questionId = entity.questionId,
        weight = entity.weight
      )
      persistentPartnerService.persist(partner)
    }

    override def updatePartner(entity: UpdatePartner): Future[Option[Partner]] = {
      persistentPartnerService.getById(entity.partnerId).flatMap {
        case Some(partner) =>
          persistentPartnerService
            .modify(
              partner.copy(
                name = entity.name,
                logo = entity.logo,
                link = entity.link,
                organisationId = entity.organisationId,
                partnerKind = entity.partnerKind,
                weight = entity.weight
              )
            )
            .map(Some.apply)
        case None => Future.successful(None)
      }
    }

    override def find(questionId: Option[QuestionId], organisationId: Option[UserId]): Future[Seq[Partner]] = {
      persistentPartnerService.find(questionId, organisationId)
    }

    override def count(questionId: Option[QuestionId], organisationId: Option[UserId]): Future[Int] = {
      persistentPartnerService.count(questionId, organisationId)
    }

  }
}

final case class CreatePartner(name: String,
                               logo: Option[String],
                               link: Option[String],
                               organisationId: Option[UserId],
                               partnerKind: PartnerKind,
                               questionId: QuestionId,
                               weight: Float)

final case class UpdatePartner(partnerId: PartnerId,
                               name: String,
                               logo: Option[String],
                               link: Option[String],
                               organisationId: Option[UserId],
                               partnerKind: PartnerKind,
                               weight: Float)

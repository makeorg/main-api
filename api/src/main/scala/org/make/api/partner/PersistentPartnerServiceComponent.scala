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

import com.typesafe.scalalogging.StrictLogging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.partner.DefaultPersistentPartnerServiceComponent.PersistentPartner
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.ShortenedNames
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import scalikejdbc._

import scala.concurrent.Future

trait PersistentPartnerServiceComponent {
  def persistentPartnerService: PersistentPartnerService
}

trait PersistentPartnerService {
  def persist(partner: Partner): Future[Partner]
  def modify(partner: Partner): Future[Partner]
  def getById(partnerId: PartnerId): Future[Option[Partner]]
  def find(questionId: Option[QuestionId], organisationId: Option[UserId]): Future[Seq[Partner]]
  def count(questionId: Option[QuestionId], organisationId: Option[UserId]): Future[Int]
}

trait DefaultPersistentPartnerServiceComponent extends PersistentPartnerServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentPartnerService: DefaultPersistentPartnerService = new DefaultPersistentPartnerService

  class DefaultPersistentPartnerService extends PersistentPartnerService with ShortenedNames {

    private val partnerAlias = PersistentPartner.partnerAlias

    private val column = PersistentPartner.column

    override def persist(partner: Partner): Future[Partner] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentPartner)
            .namedValues(
              column.id -> partner.partnerId.value,
              column.name -> partner.name,
              column.logo -> partner.logo,
              column.link -> partner.link,
              column.organisationId -> partner.organisationId.map(_.value),
              column.partnerKind -> partner.partnerKind.shortName,
              column.questionId -> partner.questionId.value,
              column.weight -> partner.weight
            )
        }.execute().apply()
      }).map(_ => partner)
    }

    override def modify(partner: Partner): Future[Partner] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB('WRITE).retryableTx { implicit session =>
        withSQL {
          update(PersistentPartner)
            .set(
              column.name -> partner.name,
              column.logo -> partner.logo,
              column.link -> partner.link,
              column.organisationId -> partner.organisationId.map(_.value),
              column.partnerKind -> partner.partnerKind.shortName,
              column.questionId -> partner.questionId.value,
              column.weight -> partner.weight
            )
            .where(sqls.eq(column.id, partner.partnerId.value))
        }.execute().apply()
      }).map(_ => partner)
    }

    override def getById(partnerId: PartnerId): Future[Option[Partner]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentPartner.as(partnerAlias))
            .where(sqls.eq(partnerAlias.id, partnerId.value))
        }.map(PersistentPartner.apply()).single.apply()
      }).map(_.map(_.toPartner))
    }

    override def find(questionId: Option[QuestionId], organisationId: Option[UserId]): Future[Seq[Partner]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentPartner.as(partnerAlias))
            .where(
              sqls.toAndConditionOpt(
                questionId.map(questionId         => sqls.eq(partnerAlias.questionId, questionId.value)),
                organisationId.map(organisationId => sqls.eq(partnerAlias.organisationId, organisationId.value))
              )
            )
        }.map(PersistentPartner.apply()).list().apply()
      }).map(_.map(_.toPartner))
    }

    override def count(questionId: Option[QuestionId], organisationId: Option[UserId]): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB('READ).retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentPartner.as(partnerAlias))
            .where(
              sqls.toAndConditionOpt(
                questionId.map(questionId         => sqls.eq(partnerAlias.questionId, questionId.value)),
                organisationId.map(organisationId => sqls.eq(partnerAlias.organisationId, organisationId.value))
              )
            )
        }.map(_.int(1)).single.apply().getOrElse(0)
      })
    }

  }
}

object DefaultPersistentPartnerServiceComponent {

  case class PersistentPartner(id: String,
                               name: String,
                               logo: Option[String],
                               link: Option[String],
                               organisationId: Option[String],
                               partnerKind: String,
                               questionId: String,
                               weight: Float) {
    def toPartner: Partner = {
      Partner(
        partnerId = PartnerId(id),
        name = name,
        logo = logo,
        link = link,
        organisationId = organisationId.map(UserId(_)),
        partnerKind = PartnerKind.kindMap(partnerKind),
        questionId = QuestionId(questionId),
        weight = weight
      )
    }
  }

  object PersistentPartner extends SQLSyntaxSupport[PersistentPartner] with ShortenedNames with StrictLogging {
    override val columnNames: Seq[String] =
      Seq("id", "name", "logo", "link", "organisation_id", "partner_kind", "question_id", "weight")

    override val tableName: String = "partner"

    lazy val partnerAlias: SyntaxProvider[PersistentPartner] = syntax("partner")

    def apply(
      partnerResultName: ResultName[PersistentPartner] = partnerAlias.resultName
    )(resultSet: WrappedResultSet): PersistentPartner = {
      PersistentPartner.apply(
        id = resultSet.string(partnerResultName.id),
        name = resultSet.string(partnerResultName.name),
        logo = resultSet.stringOpt(partnerResultName.logo),
        link = resultSet.stringOpt(partnerResultName.link),
        organisationId = resultSet.stringOpt(partnerResultName.organisationId),
        partnerKind = resultSet.string(partnerResultName.partnerKind),
        questionId = resultSet.string(partnerResultName.questionId),
        weight = resultSet.float(partnerResultName.weight)
      )
    }
  }

}

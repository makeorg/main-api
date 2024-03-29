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

import cats.data.NonEmptyList
import grizzled.slf4j.Logging
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.partner.DefaultPersistentPartnerServiceComponent.PersistentPartner
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.Futures._
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.api.technical.ScalikeSupport._
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.QuestionId
import org.make.core.user.UserId
import org.make.core.Order
import scalikejdbc._

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait DefaultPersistentPartnerServiceComponent extends PersistentPartnerServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentPartnerService: DefaultPersistentPartnerService = new DefaultPersistentPartnerService

  class DefaultPersistentPartnerService extends PersistentPartnerService with ShortenedNames {

    private val partnerAlias = PersistentPartner.alias

    private val column = PersistentPartner.column

    override def persist(partner: Partner): Future[Partner] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentPartner)
            .namedValues(
              column.id -> partner.partnerId.value,
              column.name -> partner.name,
              column.logo -> partner.logo,
              column.link -> partner.link,
              column.organisationId -> partner.organisationId.map(_.value),
              column.partnerKind -> partner.partnerKind,
              column.questionId -> partner.questionId.value,
              column.weight -> partner.weight
            )
        }.execute().apply()
      }).map(_ => partner)
    }

    override def modify(partner: Partner): Future[Partner] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentPartner)
            .set(
              column.name -> partner.name,
              column.logo -> partner.logo,
              column.link -> partner.link,
              column.organisationId -> partner.organisationId.map(_.value),
              column.partnerKind -> partner.partnerKind,
              column.questionId -> partner.questionId.value,
              column.weight -> partner.weight
            )
            .where(sqls.eq(column.id, partner.partnerId.value))
        }.execute().apply()
      }).map(_ => partner)
    }

    override def getById(partnerId: PartnerId): Future[Option[Partner]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentPartner.as(partnerAlias))
            .where(sqls.eq(partnerAlias.id, partnerId.value))
        }.map(PersistentPartner.apply()).single().apply()
      }).map(_.map(_.toPartner))
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
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.PagingSQLBuilder[PersistentPartner] = select
            .from(PersistentPartner.as(partnerAlias))
            .where(
              sqls.toAndConditionOpt(
                questionId.map(questionId         => sqls.eq(partnerAlias.questionId, questionId.value)),
                organisationId.map(organisationId => sqls.eq(partnerAlias.organisationId, organisationId.value)),
                partnerKind.map(kind              => sqls.eq(partnerAlias.partnerKind, kind))
              )
            )

          sortOrderQuery(start, end, sort, order, query)
        }.map(PersistentPartner.apply()).list().apply()
      }).map(_.map(_.toPartner))
    }

    override def count(
      questionId: Option[QuestionId],
      organisationId: Option[UserId],
      partnerKind: Option[PartnerKind]
    ): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentPartner.as(partnerAlias))
            .where(
              sqls.toAndConditionOpt(
                questionId.map(questionId         => sqls.eq(partnerAlias.questionId, questionId.value)),
                organisationId.map(organisationId => sqls.eq(partnerAlias.organisationId, organisationId.value)),
                partnerKind.map(kind              => sqls.eq(partnerAlias.partnerKind, kind))
              )
            )
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }

    override def delete(partnerId: PartnerId): Future[Unit] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          deleteFrom(PersistentPartner)
            .where(sqls.eq(PersistentPartner.column.id, partnerId.value))
        }.execute().apply()
      }).toUnit
    }

  }
}

object DefaultPersistentPartnerServiceComponent {

  final case class PersistentPartner(
    id: String,
    name: String,
    logo: Option[String],
    link: Option[String],
    organisationId: Option[String],
    partnerKind: String,
    questionId: String,
    weight: Float
  ) {
    def toPartner: Partner = {
      Partner(
        partnerId = PartnerId(id),
        name = name,
        logo = logo,
        link = link,
        organisationId = organisationId.map(UserId(_)),
        partnerKind = PartnerKind.withValue(partnerKind),
        questionId = QuestionId(questionId),
        weight = weight
      )
    }
  }

  implicit object PersistentPartner
      extends PersistentCompanion[PersistentPartner, Partner]
      with ShortenedNames
      with Logging {

    override val columnNames: Seq[String] =
      Seq("id", "name", "logo", "link", "organisation_id", "partner_kind", "question_id", "weight")

    override val tableName: String = "partner"

    override lazy val alias: SyntaxProvider[PersistentPartner] = syntax("partner")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.name)

    def apply(
      partnerResultName: ResultName[PersistentPartner] = alias.resultName
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

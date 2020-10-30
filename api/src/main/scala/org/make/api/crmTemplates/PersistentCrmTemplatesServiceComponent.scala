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

package org.make.api.crmTemplates

import cats.data.NonEmptyList
import com.typesafe.scalalogging.StrictLogging
import org.make.api.crmTemplates.DefaultPersistentCrmTemplatesServiceComponent.PersistentCrmTemplates
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.DatabaseTransactions._
import org.make.api.technical.PersistentServiceUtils.sortOrderQuery
import org.make.api.technical.{PersistentCompanion, ShortenedNames}
import org.make.core.crmTemplate.{CrmTemplates, CrmTemplatesId, TemplateId}
import org.make.core.question.QuestionId
import scalikejdbc._

import scala.concurrent.Future
import org.make.core.technical.Pagination._

trait PersistentCrmTemplatesServiceComponent {
  def persistentCrmTemplatesService: PersistentCrmTemplatesService
}

trait PersistentCrmTemplatesService {
  def persist(crmTemplates: CrmTemplates): Future[CrmTemplates]
  def modify(crmTemplates: CrmTemplates): Future[CrmTemplates]
  def getById(crmTemplatesId: CrmTemplatesId): Future[Option[CrmTemplates]]
  def getDefaultTemplate(locale: String): Future[Option[CrmTemplates]]
  def find(
    start: Start,
    end: Option[End],
    questionId: Option[QuestionId],
    locale: Option[String]
  ): Future[Seq[CrmTemplates]]
  def count(questionId: Option[QuestionId], locale: Option[String]): Future[Int]
}

trait DefaultPersistentCrmTemplatesServiceComponent extends PersistentCrmTemplatesServiceComponent {
  this: MakeDBExecutionContextComponent =>

  override lazy val persistentCrmTemplatesService: PersistentCrmTemplatesService =
    new DefaultPersistentCrmTemplatesService

  class DefaultPersistentCrmTemplatesService
      extends PersistentCrmTemplatesService
      with ShortenedNames
      with StrictLogging {

    private val crmTemplatesAlias = PersistentCrmTemplates.alias

    private val column = PersistentCrmTemplates.column

    override def persist(crmTemplates: CrmTemplates): Future[CrmTemplates] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          insert
            .into(PersistentCrmTemplates)
            .namedValues(
              column.id -> crmTemplates.crmTemplatesId.value,
              column.questionId -> crmTemplates.questionId.map(_.value),
              column.locale -> crmTemplates.locale,
              column.registration -> crmTemplates.registration.value,
              column.welcome -> crmTemplates.welcome.value,
              column.proposalAccepted -> crmTemplates.proposalAccepted.value,
              column.proposalRefused -> crmTemplates.proposalRefused.value,
              column.forgottenPassword -> crmTemplates.forgottenPassword.value,
              column.resendRegistration -> crmTemplates.resendRegistration.value,
              column.proposalAcceptedOrganisation -> crmTemplates.proposalAcceptedOrganisation.value,
              column.proposalRefusedOrganisation -> crmTemplates.proposalRefusedOrganisation.value,
              column.forgottenPasswordOrganisation -> crmTemplates.forgottenPasswordOrganisation.value,
              column.organisationEmailChangeConfirmation -> crmTemplates.organisationEmailChangeConfirmation.value,
              column.registrationB2b -> crmTemplates.registrationB2B.value
            )
        }.execute().apply()
      }).map(_ => crmTemplates)
    }

    override def modify(crmTemplates: CrmTemplates): Future[CrmTemplates] = {
      implicit val context: EC = writeExecutionContext
      Future(NamedDB("WRITE").retryableTx { implicit session =>
        withSQL {
          update(PersistentCrmTemplates)
            .set(
              column.questionId -> crmTemplates.questionId.map(_.value),
              column.locale -> crmTemplates.locale,
              column.registration -> crmTemplates.registration.value,
              column.welcome -> crmTemplates.welcome.value,
              column.proposalAccepted -> crmTemplates.proposalAccepted.value,
              column.proposalRefused -> crmTemplates.proposalRefused.value,
              column.forgottenPassword -> crmTemplates.forgottenPassword.value,
              column.resendRegistration -> crmTemplates.resendRegistration.value,
              column.proposalAcceptedOrganisation -> crmTemplates.proposalAcceptedOrganisation.value,
              column.proposalRefusedOrganisation -> crmTemplates.proposalRefusedOrganisation.value,
              column.forgottenPasswordOrganisation -> crmTemplates.forgottenPasswordOrganisation.value,
              column.organisationEmailChangeConfirmation -> crmTemplates.organisationEmailChangeConfirmation.value,
              column.registrationB2b -> crmTemplates.registrationB2B.value
            )
            .where(sqls.eq(column.id, crmTemplates.crmTemplatesId.value))
        }.execute().apply()
      }).map(_ => crmTemplates)
    }

    override def getById(crmTemplatesId: CrmTemplatesId): Future[Option[CrmTemplates]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentCrmTemplates.as(crmTemplatesAlias))
            .where(sqls.eq(crmTemplatesAlias.id, crmTemplatesId.value))
        }.map(PersistentCrmTemplates.apply()).single().apply()
      }).map(_.map(_.toCrmTemplates))
    }

    override def getDefaultTemplate(locale: String): Future[Option[CrmTemplates]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select
            .from(PersistentCrmTemplates.as(crmTemplatesAlias))
            .where(sqls.eq(crmTemplatesAlias.locale, locale).and(sqls.isNull(crmTemplatesAlias.questionId)))
        }.map(PersistentCrmTemplates.apply()).single().apply()
      }).map(_.map(_.toCrmTemplates))
    }

    override def find(
      start: Start,
      end: Option[End],
      questionId: Option[QuestionId],
      locale: Option[String]
    ): Future[Seq[CrmTemplates]] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          val query: scalikejdbc.ConditionSQLBuilder[PersistentCrmTemplates] = select
            .from(PersistentCrmTemplates.as(crmTemplatesAlias))
            .where(
              sqls.toAndConditionOpt(
                questionId.map(questionId => sqls.eq(crmTemplatesAlias.questionId, questionId.value)),
                locale.map(locale         => sqls.eq(crmTemplatesAlias.locale, locale))
              )
            )
          sortOrderQuery(start, end, None, None, query)
        }.map(PersistentCrmTemplates.apply()).list().apply()
      }).map(_.map(_.toCrmTemplates))
    }

    override def count(questionId: Option[QuestionId], locale: Option[String]): Future[Int] = {
      implicit val context: EC = readExecutionContext
      Future(NamedDB("READ").retryableTx { implicit session =>
        withSQL {
          select(sqls.count)
            .from(PersistentCrmTemplates.as(crmTemplatesAlias))
            .where(
              sqls.toAndConditionOpt(
                questionId.map(questionId => sqls.eq(crmTemplatesAlias.questionId, questionId.value)),
                locale.map(locale         => sqls.eq(crmTemplatesAlias.locale, locale))
              )
            )
        }.map(_.int(1)).single().apply().getOrElse(0)
      })
    }

  }
}

object DefaultPersistentCrmTemplatesServiceComponent {

  final case class PersistentCrmTemplates(
    id: String,
    questionId: Option[String],
    locale: Option[String],
    registration: String,
    welcome: String,
    proposalAccepted: String,
    proposalRefused: String,
    forgottenPassword: String,
    resendRegistration: String,
    proposalAcceptedOrganisation: String,
    proposalRefusedOrganisation: String,
    forgottenPasswordOrganisation: String,
    organisationEmailChangeConfirmation: String,
    registrationB2b: String
  ) {

    def toCrmTemplates: CrmTemplates =
      CrmTemplates(
        crmTemplatesId = CrmTemplatesId(id),
        questionId = questionId.map(QuestionId(_)),
        locale = locale,
        registration = TemplateId(registration),
        welcome = TemplateId(welcome),
        proposalAccepted = TemplateId(proposalAccepted),
        proposalRefused = TemplateId(proposalRefused),
        forgottenPassword = TemplateId(forgottenPassword),
        resendRegistration = TemplateId(resendRegistration),
        proposalAcceptedOrganisation = TemplateId(proposalAcceptedOrganisation),
        proposalRefusedOrganisation = TemplateId(proposalRefusedOrganisation),
        forgottenPasswordOrganisation = TemplateId(forgottenPasswordOrganisation),
        organisationEmailChangeConfirmation = TemplateId(organisationEmailChangeConfirmation),
        registrationB2B = TemplateId(registrationB2b)
      )
  }

  implicit object PersistentCrmTemplates
      extends PersistentCompanion[PersistentCrmTemplates, CrmTemplates]
      with ShortenedNames
      with StrictLogging {

    override val columnNames: Seq[String] = Seq(
      "id",
      "question_id",
      "locale",
      "registration",
      "welcome",
      "proposal_accepted",
      "proposal_refused",
      "forgotten_password",
      "resend_registration",
      "proposal_accepted_organisation",
      "proposal_refused_organisation",
      "forgotten_password_organisation",
      "organisation_email_change_confirmation",
      "registration_b2b"
    )

    override val tableName: String = "crm_templates"

    override lazy val alias: SyntaxProvider[PersistentCrmTemplates] = syntax("crmTemplates")

    override lazy val defaultSortColumns: NonEmptyList[SQLSyntax] = NonEmptyList.of(alias.id)

    def apply(
      crmTemplatesResultName: ResultName[PersistentCrmTemplates] = alias.resultName
    )(resultSet: WrappedResultSet): PersistentCrmTemplates = {
      PersistentCrmTemplates.apply(
        id = resultSet.string(crmTemplatesResultName.id),
        questionId = resultSet.stringOpt(crmTemplatesResultName.questionId),
        locale = resultSet.stringOpt(crmTemplatesResultName.locale),
        registration = resultSet.string(crmTemplatesResultName.registration),
        welcome = resultSet.string(crmTemplatesResultName.welcome),
        proposalAccepted = resultSet.string(crmTemplatesResultName.proposalAccepted),
        proposalRefused = resultSet.string(crmTemplatesResultName.proposalRefused),
        forgottenPassword = resultSet.string(crmTemplatesResultName.forgottenPassword),
        resendRegistration = resultSet.string(crmTemplatesResultName.resendRegistration),
        proposalAcceptedOrganisation = resultSet.string(crmTemplatesResultName.proposalAcceptedOrganisation),
        proposalRefusedOrganisation = resultSet.string(crmTemplatesResultName.proposalRefusedOrganisation),
        forgottenPasswordOrganisation = resultSet.string(crmTemplatesResultName.forgottenPasswordOrganisation),
        organisationEmailChangeConfirmation =
          resultSet.string(crmTemplatesResultName.organisationEmailChangeConfirmation),
        registrationB2b = resultSet.string(crmTemplatesResultName.registrationB2b)
      )
    }

  }
}

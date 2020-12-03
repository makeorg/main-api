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

package org.make.api.migrations.db

import java.sql.Connection
import java.util.concurrent.Executors

import com.typesafe.scalalogging.StrictLogging
import org.make.api.crmTemplates.{
  DefaultCrmTemplatesServiceComponent,
  DefaultPersistentCrmLanguageTemplateServiceComponent,
  DefaultPersistentCrmQuestionTemplateServiceComponent,
  DefaultPersistentCrmTemplatesServiceComponent
}
import org.make.api.extensions.MakeDBExecutionContextComponent
import org.make.api.technical.{DefaultIdGeneratorComponent, ShortenedNames}
import org.make.api.technical.ExecutorServiceHelper._
import org.make.core.crmTemplate.{CrmQuestionTemplate, CrmQuestionTemplateId, CrmTemplateKind, CrmTemplates, TemplateId}
import org.make.core.reference.Language
import org.make.core.technical.Pagination.Start

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class V98__Split_CRM_templates_by_language_and_question
    extends Migration
    with DefaultCrmTemplatesServiceComponent
    with DefaultIdGeneratorComponent
    with DefaultPersistentCrmLanguageTemplateServiceComponent
    with DefaultPersistentCrmQuestionTemplateServiceComponent
    with DefaultPersistentCrmTemplatesServiceComponent
    with MakeDBExecutionContextComponent
    with ShortenedNames
    with StrictLogging {

  private implicit val ec: EC = Executors.newFixedThreadPool(8).toExecutionContext
  override val readExecutionContext: EC = ec
  override val writeExecutionContext: EC = ec

  override def migrate(connection: Connection): Unit = {
    val migration: Future[Unit] = crmTemplatesService.find(Start.zero, None, None, None).flatMap { existingTemplates =>
      val (existingQuestionTemplates, existingLanguageTemplates) = existingTemplates.partition(_.questionId.isDefined)
      val (languageTemplatesToKeep, languageTemplatesToDrop) =
        existingLanguageTemplates.partition(templates => parseLanguage(templates).map(keep).getOrElse(false))
      languageTemplatesToDrop
        .foreach(templates => logger.warn(s"Discarding CRM templates for unsupported language ${templates.locale}"))
      languageTemplatesToKeep
        .foldLeft(Future.unit) { case (f, templates) => f.flatMap(_ => migrateLanguageTemplates(templates)) }
        .flatMap(
          _ =>
            existingQuestionTemplates.foldLeft(Future.unit) {
              case (f, templates) =>
                f.flatMap(
                  _ => migrateQuestionTemplates(templates, languageTemplatesToKeep.find(_.locale == templates.locale))
                )
            }
        )
    }
    Await.result(migration, 30.seconds)
  }

  private def migrateLanguageTemplates(templates: CrmTemplates): Future[Unit] = {
    for {
      language <- Future.fromTry(parseLanguage(templates))
      result <- crmTemplatesService
        .create(language, mapTemplates(templates))
        .map(_ => logger.info(s"Migrated CRM templates for language ${language.value}"))
    } yield result
  }

  private def migrateQuestionTemplates(
    questionTemplates: CrmTemplates,
    languageTemplates: Option[CrmTemplates]
  ): Future[Unit] = {
    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    val questionId = questionTemplates.questionId.get
    val mapping = mapTemplates(questionTemplates)
    val default = languageTemplates.map(mapTemplates)
    CrmTemplateKind.values.foldLeft(Future.unit) {
      case (f, kind) =>
        f.flatMap { _ =>
          val defaultId = default.map(_(kind))
          val templateId = mapping(kind)
          if (!defaultId.contains(templateId)) {
            logger.info(
              s"Template $kind for questionId $questionId differs from default $defaultId for locale ${questionTemplates.locale}, creating it"
            )
            crmTemplatesService
              .create(CrmQuestionTemplate(CrmQuestionTemplateId(idGenerator.nextId()), kind, questionId, mapping(kind)))
              .map(_ => ())
          } else {
            logger.info(
              s"Template $kind for questionId $questionId is the same as default $defaultId for locale ${questionTemplates.locale}, discarding it"
            )
            Future.unit
          }
        }
    }
  }

  private def mapTemplates(templates: CrmTemplates): CrmTemplateKind => TemplateId = {
    case CrmTemplateKind.Registration         => templates.registration
    case CrmTemplateKind.Welcome              => templates.welcome
    case CrmTemplateKind.ResendRegistration   => templates.resendRegistration
    case CrmTemplateKind.ForgottenPassword    => templates.forgottenPassword
    case CrmTemplateKind.ProposalAccepted     => templates.proposalAccepted
    case CrmTemplateKind.ProposalRefused      => templates.proposalRefused
    case CrmTemplateKind.B2BRegistration      => templates.registrationB2B
    case CrmTemplateKind.B2BEmailChanged      => templates.organisationEmailChangeConfirmation
    case CrmTemplateKind.B2BForgottenPassword => templates.forgottenPasswordOrganisation
    case CrmTemplateKind.B2BProposalAccepted  => templates.proposalAcceptedOrganisation
    case CrmTemplateKind.B2BProposalRefused   => templates.proposalRefusedOrganisation
  }

  private def parseLanguage(templates: CrmTemplates): Try[Language] = templates.locale.map(_.split('_')) match {
    case Some(Array(code, _)) => Success(Language(code))
    case Some(_) =>
      Failure(
        new IllegalStateException(s"Invalid locale ${templates.locale} for templates ${templates.crmTemplatesId}")
      )
    case None => Failure(new IllegalStateException(s"No locale for templates ${templates.crmTemplatesId}"))
  }

  private def keep(language: Language): Boolean = Seq("fr", "en").contains(language.value)
}

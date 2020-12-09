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

import cats.instances.future._
import cats.instances.option._
import cats.syntax.traverse._
import com.typesafe.scalalogging.StrictLogging
import org.make.api.technical._
import org.make.api.technical.RichFutures._
import org.make.core.crmTemplate.{
  CrmLanguageTemplate,
  CrmLanguageTemplateId,
  CrmQuestionTemplate,
  CrmQuestionTemplateId,
  CrmTemplate,
  CrmTemplateKind,
  CrmTemplates,
  CrmTemplatesId,
  TemplateId
}
import org.make.core.question.QuestionId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.BusinessConfig._
import org.make.core.Validation._
import org.make.core.reference.{Country, Language}
import org.make.core.technical.Pagination._

import scala.util.{Failure, Success, Try}

trait CrmTemplatesServiceComponent {
  def crmTemplatesService: CrmTemplatesService
}

trait CrmTemplatesService extends ShortenedNames {
  def getCrmTemplates(crmTemplatesId: CrmTemplatesId): Future[Option[CrmTemplates]]
  def createCrmTemplates(entity: CreateCrmTemplates): Future[CrmTemplates]
  def updateCrmTemplates(entity: UpdateCrmTemplates): Future[Option[CrmTemplates]]
  def find(
    start: Start,
    end: Option[End],
    questionId: Option[QuestionId],
    locale: Option[String]
  ): Future[Seq[CrmTemplates]]
  def count(questionId: Option[QuestionId], locale: Option[String]): Future[Int]
  def getDefaultTemplate(locale: Option[String]): Future[Option[CrmTemplates]]
  def findOne(questionId: Option[QuestionId], locale: String): Future[Option[CrmTemplates]]

  def find(kind: CrmTemplateKind, questionId: Option[QuestionId], country: Country): Future[Option[TemplateId]]

  def listByLanguage(): Future[Map[Language, CrmTemplateKind => CrmLanguageTemplate]]
  def get(language: Language): Future[Option[CrmTemplateKind => CrmLanguageTemplate]]
  def create(language: Language, values: CrmTemplateKind => TemplateId): Future[CrmTemplateKind => CrmLanguageTemplate]
  def update(language: Language, values: CrmTemplateKind => TemplateId): Future[CrmTemplateKind => CrmLanguageTemplate]

  def list(questionId: QuestionId): Future[Seq[CrmQuestionTemplate]]
  def get(id: CrmQuestionTemplateId): Future[Option[CrmQuestionTemplate]]
  def create(template: CrmQuestionTemplate): Future[CrmQuestionTemplate]
  def update(template: CrmQuestionTemplate): Future[CrmQuestionTemplate]
  def delete(id: CrmQuestionTemplateId): Future[Unit]
}

trait DefaultCrmTemplatesServiceComponent extends CrmTemplatesServiceComponent {
  this: PersistentCrmLanguageTemplateServiceComponent
    with PersistentCrmQuestionTemplateServiceComponent
    with PersistentCrmTemplatesServiceComponent
    with IdGeneratorComponent =>

  override lazy val crmTemplatesService: CrmTemplatesService = new DefaultCrmTemplatesService

  class DefaultCrmTemplatesService extends CrmTemplatesService with StrictLogging {

    override def getCrmTemplates(crmTemplatesId: CrmTemplatesId): Future[Option[CrmTemplates]] = {
      persistentCrmTemplatesService.getById(crmTemplatesId)
    }

    override def createCrmTemplates(entity: CreateCrmTemplates): Future[CrmTemplates] = {
      val crmTemplates: CrmTemplates = CrmTemplates(
        crmTemplatesId = idGenerator.nextCrmTemplatesId(),
        questionId = entity.questionId,
        locale = entity.locale,
        registration = entity.registration,
        welcome = entity.welcome,
        proposalAccepted = entity.proposalAccepted,
        proposalRefused = entity.proposalRefused,
        forgottenPassword = entity.forgottenPassword,
        resendRegistration = entity.resendRegistration,
        proposalAcceptedOrganisation = entity.proposalAcceptedOrganisation,
        proposalRefusedOrganisation = entity.proposalRefusedOrganisation,
        forgottenPasswordOrganisation = entity.forgottenPasswordOrganisation,
        organisationEmailChangeConfirmation = entity.organisationEmailChangeConfirmation,
        registrationB2B = entity.registrationB2B
      )
      persistentCrmTemplatesService.persist(crmTemplates)
    }

    override def updateCrmTemplates(entity: UpdateCrmTemplates): Future[Option[CrmTemplates]] = {
      persistentCrmTemplatesService.getById(entity.crmTemplatesId).flatMap {
        case Some(crmTemplates) =>
          persistentCrmTemplatesService
            .modify(
              crmTemplates.copy(
                registration = entity.registration,
                welcome = entity.welcome,
                proposalAccepted = entity.proposalAccepted,
                proposalRefused = entity.proposalRefused,
                forgottenPassword = entity.forgottenPassword,
                resendRegistration = entity.resendRegistration,
                proposalAcceptedOrganisation = entity.proposalAcceptedOrganisation,
                proposalRefusedOrganisation = entity.proposalRefusedOrganisation,
                forgottenPasswordOrganisation = entity.forgottenPasswordOrganisation,
                organisationEmailChangeConfirmation = entity.organisationEmailChangeConfirmation,
                registrationB2B = entity.registrationB2B
              )
            )
            .map(Some.apply)
        case None => Future.successful(None)
      }
    }

    def findOne(questionId: Option[QuestionId], locale: String): Future[Option[CrmTemplates]] = {
      val result = questionId
        .traverse(_ => persistentCrmTemplatesService.find(Start.zero, Some(End(1)), questionId, None).map(_.headOption))
        .flatMap {
          case Some(templates @ Some(_)) => Future.successful(templates)
          case _                         => persistentCrmTemplatesService.getDefaultTemplate(locale)
        }

      result.foreach(
        r =>
          if (r.isEmpty)
            logger.error(s"No templates found for question: ${questionId.toString} and locale $locale.")
      )

      result
    }

    override def find(
      start: Start,
      end: Option[End],
      questionId: Option[QuestionId],
      locale: Option[String]
    ): Future[Seq[CrmTemplates]] = {
      val searchByLocale: Option[String] = questionId match {
        case Some(_) => None
        case None    => locale
      }
      persistentCrmTemplatesService.find(start, end, questionId, searchByLocale).flatMap {
        case crmTemplates if crmTemplates.nonEmpty || locale.isEmpty => Future.successful(crmTemplates)
        case _                                                       => persistentCrmTemplatesService.find(start, end, None, locale)
      }
    }

    override def count(questionId: Option[QuestionId], locale: Option[String]): Future[Int] = {
      val searchByLocale: Option[String] = questionId match {
        case Some(_) => None
        case None    => locale
      }
      persistentCrmTemplatesService.count(questionId, searchByLocale)
    }

    override def getDefaultTemplate(locale: Option[String]): Future[Option[CrmTemplates]] = {
      locale.fold(Future.successful[Option[CrmTemplates]](None))(persistentCrmTemplatesService.getDefaultTemplate)
    }

    override def find(
      kind: CrmTemplateKind,
      questionId: Option[QuestionId],
      country: Country
    ): Future[Option[TemplateId]] = {

      val byQuestion: () => Future[Option[Option[CrmTemplate]]] = () =>
        questionId.traverse(id => list(id).map(_.find(_.kind == kind)))
      val byLanguage = () => country.languageOption.traverse(language => get(language).map(_.map(_(kind))))
      val default = ()    => get(Language("en")).map(_.map(_(kind)))

      val result = byQuestion.or(byLanguage.or(default)).apply().map(_.map(_.template))

      result.foreach(
        r =>
          if (r.isEmpty)
            logger.error(s"No $kind template found for question: $questionId and country $country.")
      )

      result
    }

    // Language templates

    override def listByLanguage(): Future[Map[Language, CrmTemplateKind => CrmLanguageTemplate]] = {
      persistentCrmLanguageTemplateService
        .all()
        .map(
          all =>
            all
              .groupBy(_.language)
              .map {
                case (language, templates) =>
                  val validated = validate(language, templates)
                  validated.failed.foreach(logger.error("Listing an invalid language in CRM language templates", _))
                  language -> validated
              }
              .collect { case (language, Success(templates)) => language -> templates }
        )
    }

    override def get(language: Language): Future[Option[CrmTemplateKind => CrmLanguageTemplate]] = {
      persistentCrmLanguageTemplateService.list(language).flatMap { raw =>
        if (raw.isEmpty) {
          Future.successful(None)
        } else {
          Future.fromTry(validate(language, raw)).map(Some.apply)
        }
      }
    }

    override def create(
      language: Language,
      values: CrmTemplateKind => TemplateId
    ): Future[CrmTemplateKind => CrmLanguageTemplate] = {
      val templates = CrmTemplateKind.values.map(
        kind => CrmLanguageTemplate(CrmLanguageTemplateId(idGenerator.nextId()), kind, language, values(kind))
      )
      persistentCrmLanguageTemplateService
        .persist(templates)
        .flatMap(result => Future.fromTry(validate(language, result)))
    }

    override def update(
      language: Language,
      values: CrmTemplateKind => TemplateId
    ): Future[CrmTemplateKind => CrmLanguageTemplate] = {
      persistentCrmLanguageTemplateService
        .list(language)
        .flatMap(
          current =>
            validate(language, current) match {
              case Success(_) =>
                persistentCrmLanguageTemplateService
                  .modify(current.map(template => template.copy(template = values(template.kind))))
                  .flatMap(result => Future.fromTry(validate(language, result)))
              case Failure(e) => Future.failed(e)
            }
        )
    }

    private def validate(
      language: Language,
      templates: Seq[CrmLanguageTemplate]
    ): Try[CrmTemplateKind => CrmLanguageTemplate] = {
      val indexed = templates.groupMapReduce(_.kind)(identity)((template, _) => template)
      val missing = CrmTemplateKind.values.toSet -- indexed.keys
      if (missing.isEmpty) {
        Success(indexed.apply)
      } else {
        Failure(
          new IllegalStateException(s"Missing CRM language templates for ${language.value}: ${missing.mkString(", ")}")
        )
      }
    }

    // Question templates

    override def list(questionId: QuestionId): Future[Seq[CrmQuestionTemplate]] = {
      persistentCrmQuestionTemplateService.list(questionId)
    }

    override def get(id: CrmQuestionTemplateId): Future[Option[CrmQuestionTemplate]] = {
      persistentCrmQuestionTemplateService.get(id)
    }

    override def create(template: CrmQuestionTemplate): Future[CrmQuestionTemplate] = {
      persistentCrmQuestionTemplateService.persist(template)
    }

    override def update(template: CrmQuestionTemplate): Future[CrmQuestionTemplate] = {
      persistentCrmQuestionTemplateService.modify(template)
    }

    override def delete(id: CrmQuestionTemplateId): Future[Unit] = {
      persistentCrmQuestionTemplateService.remove(id)
    }

  }
}

final case class CreateCrmTemplates(
  questionId: Option[QuestionId],
  locale: Option[String],
  registration: TemplateId,
  welcome: TemplateId,
  proposalAccepted: TemplateId,
  proposalRefused: TemplateId,
  forgottenPassword: TemplateId,
  resendRegistration: TemplateId,
  proposalAcceptedOrganisation: TemplateId,
  proposalRefusedOrganisation: TemplateId,
  forgottenPasswordOrganisation: TemplateId,
  organisationEmailChangeConfirmation: TemplateId,
  registrationB2B: TemplateId
) {
  validate(
    validateField(
      "registration",
      "invalid_content",
      registration.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField("welcome", "invalid_content", welcome.value.toIntOption.isDefined, "TemplateId must be of type Int"),
    validateField(
      "proposalAccepted",
      "invalid_content",
      proposalAccepted.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalRefused",
      "invalid_content",
      proposalRefused.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "forgottenPassword",
      "invalid_content",
      forgottenPassword.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalAcceptedOrganisation",
      "invalid_content",
      proposalAcceptedOrganisation.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalRefusedOrganisation",
      "invalid_content",
      proposalRefusedOrganisation.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "forgottenPasswordOrganisation",
      "invalid_content",
      forgottenPasswordOrganisation.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "organisationEmailChangeConfirmation",
      "invalid_content",
      organisationEmailChangeConfirmation.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "registrationB2B",
      "invalid_content",
      registrationB2B.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    )
  )
}

final case class UpdateCrmTemplates(
  crmTemplatesId: CrmTemplatesId,
  registration: TemplateId,
  welcome: TemplateId,
  proposalAccepted: TemplateId,
  proposalRefused: TemplateId,
  forgottenPassword: TemplateId,
  resendRegistration: TemplateId,
  proposalAcceptedOrganisation: TemplateId,
  proposalRefusedOrganisation: TemplateId,
  forgottenPasswordOrganisation: TemplateId,
  organisationEmailChangeConfirmation: TemplateId,
  registrationB2B: TemplateId
) {
  validate(
    validateField(
      "registration",
      "invalid_content",
      registration.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField("welcome", "invalid_content", welcome.value.toIntOption.isDefined, "TemplateId must be of type Int"),
    validateField(
      "proposalAccepted",
      "invalid_content",
      proposalAccepted.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalRefused",
      "invalid_content",
      proposalRefused.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "forgottenPassword",
      "invalid_content",
      forgottenPassword.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalAcceptedOrganisation",
      "invalid_content",
      proposalAcceptedOrganisation.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "proposalRefusedOrganisation",
      "invalid_content",
      proposalRefusedOrganisation.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "forgottenPasswordOrganisation",
      "invalid_content",
      forgottenPasswordOrganisation.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "organisationEmailChangeConfirmation",
      "invalid_content",
      organisationEmailChangeConfirmation.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    ),
    validateField(
      "registrationB2B",
      "invalid_content",
      registrationB2B.value.toIntOption.isDefined,
      "TemplateId must be of type Int"
    )
  )

}

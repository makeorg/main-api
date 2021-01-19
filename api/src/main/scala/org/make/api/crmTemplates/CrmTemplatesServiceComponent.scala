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
import grizzled.slf4j.Logging
import org.make.api.technical._
import org.make.api.technical.RichFutures._
import org.make.core.crmTemplate.{
  CrmLanguageTemplate,
  CrmLanguageTemplateId,
  CrmQuestionTemplate,
  CrmQuestionTemplateId,
  CrmTemplate,
  CrmTemplateKind,
  TemplateId
}
import org.make.core.question.QuestionId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.make.core.BusinessConfig._
import org.make.core.reference.{Country, Language}

import scala.collection.SortedMap
import scala.util.{Failure, Success, Try}

trait CrmTemplatesServiceComponent {
  def crmTemplatesService: CrmTemplatesService
}

trait CrmTemplatesService extends ShortenedNames {
  def find(kind: CrmTemplateKind, questionId: Option[QuestionId], country: Country): Future[Option[TemplateId]]

  def listByLanguage(): Future[SortedMap[Language, CrmTemplateKind => CrmLanguageTemplate]]
  def get(language: Language): Future[Option[CrmTemplateKind       => CrmLanguageTemplate]]
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
    with IdGeneratorComponent =>

  override lazy val crmTemplatesService: CrmTemplatesService = new DefaultCrmTemplatesService

  class DefaultCrmTemplatesService extends CrmTemplatesService with Logging {

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

    override def listByLanguage(): Future[SortedMap[Language, CrmTemplateKind => CrmLanguageTemplate]] = {
      persistentCrmLanguageTemplateService
        .all()
        .map(
          all =>
            SortedMap.from(
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

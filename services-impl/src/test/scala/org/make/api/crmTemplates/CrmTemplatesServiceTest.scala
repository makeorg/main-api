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

import org.make.api.MakeUnitTest
import org.make.api.technical.IdGeneratorComponent
import org.make.core.crmTemplate.{
  CrmLanguageTemplate,
  CrmLanguageTemplateId,
  CrmQuestionTemplate,
  CrmQuestionTemplateId,
  CrmTemplateKind,
  TemplateId
}
import org.make.core.question.QuestionId
import org.make.core.reference.{Country, Language}
import org.make.core.technical.IdGenerator
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class CrmTemplatesServiceTest
    extends MakeUnitTest
    with DefaultCrmTemplatesServiceComponent
    with PersistentCrmLanguageTemplateServiceComponent
    with PersistentCrmQuestionTemplateServiceComponent
    with IdGeneratorComponent {

  override val persistentCrmLanguageTemplateService: PersistentCrmLanguageTemplateService =
    mock[PersistentCrmLanguageTemplateService]
  override val persistentCrmQuestionTemplateService: PersistentCrmQuestionTemplateService =
    mock[PersistentCrmQuestionTemplateService]
  override val idGenerator: IdGenerator = mock[IdGenerator]

  Feature("language templates") {

    val frenchTemplates = CrmTemplateKind.values.zipWithIndex.map {
      case (kind, i) =>
        CrmLanguageTemplate(CrmLanguageTemplateId(i.toString), kind, Language("fr"), TemplateId(s"french-$i"))
    }

    Scenario("list languages") {
      when(persistentCrmLanguageTemplateService.all())
        .thenReturn(
          Future.successful(
            frenchTemplates ++ frenchTemplates.map(_.copy(language = Language("es"))) :+ CrmLanguageTemplate(
              CrmLanguageTemplateId("belgian"),
              CrmTemplateKind.Welcome,
              Language("be"),
              TemplateId("1")
            )
          )
        )
      whenReady(crmTemplatesService.listByLanguage(), Timeout(5.seconds)) { list =>
        list.toSeq.map(_._1) shouldBe Seq(Language("es"), Language("fr"))
      }
    }

    Scenario("get templates for a configured language") {
      when(persistentCrmLanguageTemplateService.list(Language("fr"))).thenReturn(Future.successful(frenchTemplates))
      whenReady(crmTemplatesService.get(Language("fr"))) { result =>
        result shouldBe defined
        val mapping = result.get
        CrmTemplateKind.values.zipWithIndex.foreach {
          case (kind, i) => mapping(kind).template.value shouldBe s"french-$i"
        }
      }
    }

    Scenario("get templates for a non-configured language") {
      when(persistentCrmLanguageTemplateService.list(Language("be"))).thenReturn(Future.successful(Nil))
      whenReady(crmTemplatesService.get(Language("be"))) {
        _ shouldBe None
      }
    }

    Scenario("get templates for a misconfigured language") {
      when(persistentCrmLanguageTemplateService.list(Language("es"))).thenReturn(
        Future.successful(
          Seq(
            CrmLanguageTemplate(
              CrmLanguageTemplateId("foo"),
              CrmTemplateKind.Welcome,
              Language("es"),
              TemplateId("bar")
            )
          )
        )
      )
      whenReady(crmTemplatesService.get(Language("es")).failed) {
        _.getMessage shouldBe s"Missing CRM language templates for es: ${(CrmTemplateKind.values.toSet - CrmTemplateKind.Welcome)
          .mkString(", ")}"
      }
    }

    Scenario("create") {
      when(persistentCrmLanguageTemplateService.persist(any[Seq[CrmLanguageTemplate]]))
        .thenAnswer[Seq[CrmLanguageTemplate]](Future.successful)
      whenReady(
        crmTemplatesService.create(Language("en"), kind => TemplateId(CrmTemplateKind.values.indexOf(kind).toString))
      ) { result =>
        CrmTemplateKind.values.zipWithIndex.foreach {
          case (kind, i) =>
            val template = result(kind)
            template.language shouldBe Language("en")
            template.template.value shouldBe i.toString
        }
      }
    }

    Scenario("update") {
      val templates = CrmTemplateKind.values.zipWithIndex.map {
        case (kind, i) =>
          CrmLanguageTemplate(
            CrmLanguageTemplateId(s"english-${kind.entryName}"),
            kind,
            Language("en"),
            TemplateId(s"english-$i")
          )
      }
      when(persistentCrmLanguageTemplateService.list(Language("en"))).thenReturn(Future.successful(templates))
      when(persistentCrmLanguageTemplateService.modify(any[Seq[CrmLanguageTemplate]]))
        .thenAnswer[Seq[CrmLanguageTemplate]](Future.successful)
      whenReady(
        crmTemplatesService.update(Language("en"), kind => TemplateId(s"new-${CrmTemplateKind.values.indexOf(kind)}"))
      ) { result =>
        CrmTemplateKind.values.zipWithIndex.foreach {
          case (kind, i) =>
            val template = result(kind)
            template.language shouldBe Language("en")
            template.template.value shouldBe s"new-$i"
        }
      }
    }
  }

  Feature("question templates") {

    val questionTemplate1 = CrmQuestionTemplate(
      CrmQuestionTemplateId("foo"),
      CrmTemplateKind.ResendRegistration,
      QuestionId("baz"),
      TemplateId("baz-resend-registration")
    )
    val questionTemplate2 = CrmQuestionTemplate(
      CrmQuestionTemplateId("bar"),
      CrmTemplateKind.B2BRegistration,
      QuestionId("baz"),
      TemplateId("baz-b2b-registration")
    )

    Scenario("list question templates") {

      when(persistentCrmQuestionTemplateService.list(QuestionId("baz")))
        .thenReturn(Future.successful(Seq(questionTemplate1, questionTemplate2)))

      whenReady(crmTemplatesService.list(QuestionId("baz"))) {
        _ shouldBe Seq(questionTemplate1, questionTemplate2)
      }

    }

    Scenario("get question template") {

      when(persistentCrmQuestionTemplateService.get(CrmQuestionTemplateId("foo")))
        .thenAnswer(Future.successful(Some(questionTemplate1)))

      whenReady(crmTemplatesService.get(CrmQuestionTemplateId("foo"))) { _ shouldBe Some(questionTemplate1) }

    }

    Scenario("create question template") {

      when(persistentCrmQuestionTemplateService.persist(any[CrmQuestionTemplate]))
        .thenAnswer[CrmQuestionTemplate](Future.successful)

      whenReady(crmTemplatesService.create(questionTemplate1)) {
        _ shouldBe questionTemplate1
      }

    }

    Scenario("update question template") {

      when(persistentCrmQuestionTemplateService.modify(any[CrmQuestionTemplate]))
        .thenAnswer[CrmQuestionTemplate](Future.successful)

      val updated = questionTemplate1.copy(template = TemplateId("9"))
      whenReady(crmTemplatesService.update(updated)) {
        _ shouldBe updated
      }

    }

    Scenario("delete question template") {

      when(persistentCrmQuestionTemplateService.remove(any[CrmQuestionTemplateId]))
        .thenAnswer[CrmQuestionTemplateId](_ => Future.unit)

      whenReady(crmTemplatesService.delete(questionTemplate2.id)) {
        _ shouldBe ()
      }

    }

  }

  Feature("find CRM templates") {

    Scenario("defined by question") {
      whenReady(crmTemplatesService.find(CrmTemplateKind.ResendRegistration, Some(QuestionId("baz")), Country("AZ"))) {
        _ shouldBe Some(TemplateId("baz-resend-registration"))
      }
    }

    Scenario("undefined by question but defined by country") {
      whenReady(crmTemplatesService.find(CrmTemplateKind.ForgottenPassword, Some(QuestionId("baz")), Country("FR"))) {
        _ shouldBe Some(TemplateId(s"french-${CrmTemplateKind.values.indexOf(CrmTemplateKind.ForgottenPassword)}"))
      }
    }

    Scenario("fallback to english") {
      whenReady(crmTemplatesService.find(CrmTemplateKind.Welcome, Some(QuestionId("baz")), Country("AZ"))) {
        _ shouldBe Some(TemplateId(s"english-${CrmTemplateKind.values.indexOf(CrmTemplateKind.Welcome)}"))
      }
    }

  }

}

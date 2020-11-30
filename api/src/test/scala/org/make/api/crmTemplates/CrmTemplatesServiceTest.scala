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
  CrmTemplates,
  CrmTemplatesId,
  TemplateId
}
import org.make.core.question.QuestionId
import org.make.core.reference.Language
import org.make.core.technical.IdGenerator
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future
import org.make.core.technical.Pagination.Start

class CrmTemplatesServiceTest
    extends MakeUnitTest
    with DefaultCrmTemplatesServiceComponent
    with PersistentCrmLanguageTemplateServiceComponent
    with PersistentCrmQuestionTemplateServiceComponent
    with PersistentCrmTemplatesServiceComponent
    with IdGeneratorComponent {

  override val persistentCrmLanguageTemplateService: PersistentCrmLanguageTemplateService =
    mock[PersistentCrmLanguageTemplateService]
  override val persistentCrmQuestionTemplateService: PersistentCrmQuestionTemplateService =
    mock[PersistentCrmQuestionTemplateService]
  override val persistentCrmTemplatesService: PersistentCrmTemplatesService = mock[PersistentCrmTemplatesService]
  override val idGenerator: IdGenerator = mock[IdGenerator]

  Feature("get crmTemplates") {
    Scenario("get crmTemplates from CrmTemplatesId") {
      crmTemplatesService.getCrmTemplates(CrmTemplatesId("templates-id"))

      verify(persistentCrmTemplatesService).getById(CrmTemplatesId("templates-id"))
    }
  }

  Feature("create crmTemplates") {
    Scenario("creating a crmTemplates success") {
      when(idGenerator.nextCrmTemplatesId()).thenReturn(CrmTemplatesId("next-id"))
      crmTemplatesService.createCrmTemplates(
        CreateCrmTemplates(
          questionId = Some(QuestionId("questionId")),
          locale = Some("locale"),
          registration = TemplateId("123456"),
          welcome = TemplateId("123456"),
          proposalAccepted = TemplateId("123456"),
          proposalRefused = TemplateId("123456"),
          forgottenPassword = TemplateId("123456"),
          resendRegistration = TemplateId("123456"),
          proposalAcceptedOrganisation = TemplateId("123456"),
          proposalRefusedOrganisation = TemplateId("123456"),
          forgottenPasswordOrganisation = TemplateId("123456"),
          organisationEmailChangeConfirmation = TemplateId("123456"),
          registrationB2B = TemplateId("123456")
        )
      )

      verify(persistentCrmTemplatesService)
        .persist(
          refEq[CrmTemplates](
            CrmTemplates(
              crmTemplatesId = CrmTemplatesId("next-id"),
              questionId = Some(QuestionId("questionId")),
              locale = Some("locale"),
              registration = TemplateId("123456"),
              welcome = TemplateId("123456"),
              proposalAccepted = TemplateId("123456"),
              proposalRefused = TemplateId("123456"),
              forgottenPassword = TemplateId("123456"),
              resendRegistration = TemplateId("123456"),
              proposalAcceptedOrganisation = TemplateId("123456"),
              proposalRefusedOrganisation = TemplateId("123456"),
              forgottenPasswordOrganisation = TemplateId("123456"),
              organisationEmailChangeConfirmation = TemplateId("123456"),
              registrationB2B = TemplateId("123456")
            )
          )
        )
    }
  }

  Feature("update crmTemplates") {
    Scenario("update an non existent crmTemplates") {
      when(persistentCrmTemplatesService.getById(CrmTemplatesId("fake"))).thenReturn(Future.successful(None))

      val futureCrmTemplates: Future[Option[CrmTemplates]] = crmTemplatesService.updateCrmTemplates(
        UpdateCrmTemplates(
          crmTemplatesId = CrmTemplatesId("fake"),
          registration = TemplateId("123456"),
          welcome = TemplateId("123456"),
          proposalAccepted = TemplateId("123456"),
          proposalRefused = TemplateId("123456"),
          forgottenPassword = TemplateId("123456"),
          resendRegistration = TemplateId("123456"),
          proposalAcceptedOrganisation = TemplateId("123456"),
          proposalRefusedOrganisation = TemplateId("123456"),
          forgottenPasswordOrganisation = TemplateId("123456"),
          organisationEmailChangeConfirmation = TemplateId("123456"),
          registrationB2B = TemplateId("123456")
        )
      )

      whenReady(futureCrmTemplates) { emptyResult =>
        emptyResult shouldBe empty
      }
    }

    Scenario("update crmTemplates success") {
      val updateCrmTemplates = CrmTemplates(
        crmTemplatesId = CrmTemplatesId("id"),
        questionId = Some(QuestionId("questionId")),
        locale = None,
        registration = TemplateId("123456"),
        welcome = TemplateId("123456"),
        proposalAccepted = TemplateId("123456"),
        proposalRefused = TemplateId("123456"),
        forgottenPassword = TemplateId("123456"),
        resendRegistration = TemplateId("123456"),
        proposalAcceptedOrganisation = TemplateId("123456"),
        proposalRefusedOrganisation = TemplateId("123456"),
        forgottenPasswordOrganisation = TemplateId("123456"),
        organisationEmailChangeConfirmation = TemplateId("123456"),
        registrationB2B = TemplateId("123456")
      )
      when(persistentCrmTemplatesService.getById(CrmTemplatesId("id")))
        .thenReturn(Future.successful(Some(updateCrmTemplates)))
      when(persistentCrmTemplatesService.modify(eqTo(updateCrmTemplates)))
        .thenReturn(Future.successful(updateCrmTemplates))

      val futureCrmTemplates: Future[Option[CrmTemplates]] = crmTemplatesService.updateCrmTemplates(
        UpdateCrmTemplates(
          crmTemplatesId = CrmTemplatesId("id"),
          registration = TemplateId("123456"),
          welcome = TemplateId("123456"),
          proposalAccepted = TemplateId("123456"),
          proposalRefused = TemplateId("123456"),
          forgottenPassword = TemplateId("123456"),
          resendRegistration = TemplateId("123456"),
          proposalAcceptedOrganisation = TemplateId("123456"),
          proposalRefusedOrganisation = TemplateId("123456"),
          forgottenPasswordOrganisation = TemplateId("123456"),
          organisationEmailChangeConfirmation = TemplateId("123456"),
          registrationB2B = TemplateId("123456")
        )
      )

      whenReady(futureCrmTemplates, Timeout(3.seconds)) { crmResult =>
        crmResult shouldBe Some(updateCrmTemplates)
      }
    }
  }

  Feature("find crmTemplates") {
    val aCrmTemplates = CrmTemplates(
      crmTemplatesId = CrmTemplatesId("id"),
      questionId = Some(QuestionId("toto")),
      locale = Some("locale"),
      registration = TemplateId("123456"),
      welcome = TemplateId("123456"),
      proposalAccepted = TemplateId("123456"),
      proposalRefused = TemplateId("123456"),
      forgottenPassword = TemplateId("123456"),
      resendRegistration = TemplateId("123456"),
      proposalAcceptedOrganisation = TemplateId("123456"),
      proposalRefusedOrganisation = TemplateId("123456"),
      forgottenPasswordOrganisation = TemplateId("123456"),
      organisationEmailChangeConfirmation = TemplateId("123456"),
      registrationB2B = TemplateId("123456")
    )

    Scenario("find crmTemplates from CrmTemplatesId with questionId and locale fallback") {
      when(persistentCrmTemplatesService.find(eqTo(Start.zero), eqTo(None), eqTo(Some(QuestionId("toto"))), eqTo(None)))
        .thenReturn(Future.successful(Seq.empty))
      when(persistentCrmTemplatesService.find(eqTo(Start.zero), eqTo(None), eqTo(None), eqTo(Some("locale-fallback"))))
        .thenReturn(Future.successful(Seq(aCrmTemplates, aCrmTemplates)))
      whenReady(
        crmTemplatesService.find(Start.zero, None, Some(QuestionId("toto")), Some("locale-fallback")),
        Timeout(3.seconds)
      ) { result =>
        result.size shouldBe 2
      }
    }

    Scenario("find crmTemplates from CrmTemplatesId without questionId with locale") {
      when(persistentCrmTemplatesService.find(eqTo(Start.zero), eqTo(None), eqTo(None), eqTo(Some("locale"))))
        .thenReturn(Future.successful(Seq(aCrmTemplates)))

      whenReady(crmTemplatesService.find(Start.zero, None, None, Some("locale")), Timeout(3.seconds)) { result =>
        result.head.crmTemplatesId shouldBe CrmTemplatesId("id")
      }
    }

    Scenario("find crmTemplates from CrmTemplatesId without params") {
      when(persistentCrmTemplatesService.find(eqTo(Start.zero), eqTo(None), eqTo(None), eqTo(None)))
        .thenReturn(Future.successful(Seq.empty))

      whenReady(crmTemplatesService.find(Start.zero, None, None, None), Timeout(3.seconds)) { result =>
        result.isEmpty shouldBe true
      }
    }
  }

  Feature("count crmTemplates") {
    Scenario("count crmTemplates from CrmTemplatesId with questionId ignore locale") {
      crmTemplatesService.count(Some(QuestionId("toto")), Some("locale-ignored"))

      verify(persistentCrmTemplatesService).count(Some(QuestionId("toto")), None)
    }

    Scenario("count crmTemplates from CrmTemplatesId without questionId with locale") {
      crmTemplatesService.count(None, Some("locale"))

      verify(persistentCrmTemplatesService).count(None, Some("locale"))
    }

    Scenario("count crmTemplates from CrmTemplatesId without params") {
      crmTemplatesService.count(None, None)

      verify(persistentCrmTemplatesService).count(None, None)
    }
  }

  Feature("language templates") {

    val frenchTemplates = CrmTemplateKind.values.zipWithIndex.map {
      case (kind, i) =>
        CrmLanguageTemplate(CrmLanguageTemplateId(i.toString), kind, Language("FR"), TemplateId(i.toString))
    }

    Scenario("list languages") {
      when(persistentCrmLanguageTemplateService.all())
        .thenReturn(
          Future.successful(
            frenchTemplates :+ CrmLanguageTemplate(
              CrmLanguageTemplateId("belgian"),
              CrmTemplateKind.Welcome,
              Language("BE"),
              TemplateId("1")
            )
          )
        )
      whenReady(crmTemplatesService.listByLanguage()) {
        _.keys shouldBe Set(Language("FR"))
      }
    }

    Scenario("get templates for a configured language") {
      when(persistentCrmLanguageTemplateService.list(Language("FR"))).thenReturn(Future.successful(frenchTemplates))
      whenReady(crmTemplatesService.get(Language("FR"))) { result =>
        result shouldBe defined
        val mapping = result.get
        CrmTemplateKind.values.zipWithIndex.foreach {
          case (kind, i) => mapping(kind).template.value shouldBe i.toString
        }
      }
    }

    Scenario("get templates for a non-configured language") {
      when(persistentCrmLanguageTemplateService.list(Language("BE"))).thenReturn(Future.successful(Nil))
      whenReady(crmTemplatesService.get(Language("BE"))) {
        _ shouldBe None
      }
    }

    Scenario("get templates for a misconfigured language") {
      when(persistentCrmLanguageTemplateService.list(Language("ES"))).thenReturn(
        Future.successful(
          Seq(
            CrmLanguageTemplate(
              CrmLanguageTemplateId("foo"),
              CrmTemplateKind.Welcome,
              Language("ES"),
              TemplateId("bar")
            )
          )
        )
      )
      whenReady(crmTemplatesService.get(Language("ES")).failed) {
        _.getMessage shouldBe s"Missing CRM language templates for ES: ${(CrmTemplateKind.values.toSet - CrmTemplateKind.Welcome)
          .mkString(", ")}"
      }
    }

    Scenario("create") {
      when(persistentCrmLanguageTemplateService.persist(any[Seq[CrmLanguageTemplate]]))
        .thenAnswer[Seq[CrmLanguageTemplate]](Future.successful)
      whenReady(
        crmTemplatesService.create(Language("EN"), kind => TemplateId(CrmTemplateKind.values.indexOf(kind).toString))
      ) { result =>
        CrmTemplateKind.values.zipWithIndex.foreach {
          case (kind, i) =>
            val template = result(kind)
            template.language shouldBe Language("EN")
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
            Language("EN"),
            TemplateId(i.toString)
          )
      }
      when(persistentCrmLanguageTemplateService.list(Language("EN"))).thenReturn(Future.successful(templates))
      when(persistentCrmLanguageTemplateService.modify(any[Seq[CrmLanguageTemplate]]))
        .thenAnswer[Seq[CrmLanguageTemplate]](Future.successful)
      whenReady(
        crmTemplatesService.update(Language("EN"), kind => TemplateId(s"new-${CrmTemplateKind.values.indexOf(kind)}"))
      ) { result =>
        CrmTemplateKind.values.zipWithIndex.foreach {
          case (kind, i) =>
            val template = result(kind)
            template.language shouldBe Language("EN")
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
      TemplateId("1")
    )
    val questionTemplate2 = CrmQuestionTemplate(
      CrmQuestionTemplateId("bar"),
      CrmTemplateKind.B2BRegistration,
      QuestionId("baz"),
      TemplateId("2")
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
        .thenAnswer[CrmQuestionTemplateId](_ => Future.successful {})

      whenReady(crmTemplatesService.delete(questionTemplate2.id)) {
        _ shouldBe ()
      }

    }

  }

}

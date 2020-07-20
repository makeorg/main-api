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
import org.make.core.crmTemplate.{CrmTemplates, CrmTemplatesId, TemplateId}
import org.make.core.question.QuestionId
import org.make.core.technical.IdGenerator
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class CrmTemplatesServiceTest
    extends MakeUnitTest
    with DefaultCrmTemplatesServiceComponent
    with PersistentCrmTemplatesServiceComponent
    with IdGeneratorComponent {

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
      when(persistentCrmTemplatesService.find(eqTo(0), eqTo(None), eqTo(Some(QuestionId("toto"))), eqTo(None)))
        .thenReturn(Future.successful(Seq.empty))
      when(persistentCrmTemplatesService.find(eqTo(0), eqTo(None), eqTo(None), eqTo(Some("locale-fallback"))))
        .thenReturn(Future.successful(Seq(aCrmTemplates, aCrmTemplates)))
      whenReady(
        crmTemplatesService.find(0, None, Some(QuestionId("toto")), Some("locale-fallback")),
        Timeout(3.seconds)
      ) { result =>
        result.size shouldBe 2
      }
    }

    Scenario("find crmTemplates from CrmTemplatesId without questionId with locale") {
      when(persistentCrmTemplatesService.find(eqTo(0), eqTo(None), eqTo(None), eqTo(Some("locale"))))
        .thenReturn(Future.successful(Seq(aCrmTemplates)))

      whenReady(crmTemplatesService.find(0, None, None, Some("locale")), Timeout(3.seconds)) { result =>
        result.head.crmTemplatesId shouldBe CrmTemplatesId("id")
      }
    }

    Scenario("find crmTemplates from CrmTemplatesId without params") {
      when(persistentCrmTemplatesService.find(eqTo(0), eqTo(None), eqTo(None), eqTo(None)))
        .thenReturn(Future.successful(Seq.empty))

      whenReady(crmTemplatesService.find(0, None, None, None), Timeout(3.seconds)) { result =>
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
}

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
import org.mockito.{ArgumentMatchers, Mockito}
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

  feature("get crmTemplates") {
    scenario("get crmTemplates from CrmTemplatesId") {
      crmTemplatesService.getCrmTemplates(CrmTemplatesId("templates-id"))

      Mockito.verify(persistentCrmTemplatesService).getById(CrmTemplatesId("templates-id"))
    }
  }

  feature("create crmTemplates") {
    scenario("creating a crmTemplates success") {
      Mockito.when(idGenerator.nextCrmTemplatesId()).thenReturn(CrmTemplatesId("next-id"))
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

      Mockito
        .verify(persistentCrmTemplatesService)
        .persist(
          ArgumentMatchers.refEq[CrmTemplates](
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

  feature("update crmTemplates") {
    scenario("update an non existent crmTemplates") {
      Mockito.when(persistentCrmTemplatesService.getById(CrmTemplatesId("fake"))).thenReturn(Future.successful(None))

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

    scenario("update crmTemplates success") {
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
      Mockito
        .when(persistentCrmTemplatesService.getById(CrmTemplatesId("id")))
        .thenReturn(Future.successful(Some(updateCrmTemplates)))
      Mockito
        .when(persistentCrmTemplatesService.modify(ArgumentMatchers.eq(updateCrmTemplates)))
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

  feature("find crmTemplates") {
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

    scenario("find crmTemplates from CrmTemplatesId with questionId and locale fallback") {
      Mockito
        .when(
          persistentCrmTemplatesService.find(
            ArgumentMatchers.eq(0),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(Some(QuestionId("toto"))),
            ArgumentMatchers.eq(None)
          )
        )
        .thenReturn(Future.successful(Seq.empty))
      Mockito
        .when(
          persistentCrmTemplatesService.find(
            ArgumentMatchers.eq(0),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(Some("locale-fallback"))
          )
        )
        .thenReturn(Future.successful(Seq(aCrmTemplates, aCrmTemplates)))
      whenReady(
        crmTemplatesService.find(0, None, Some(QuestionId("toto")), Some("locale-fallback")),
        Timeout(3.seconds)
      ) { result =>
        result.size shouldBe 2
      }
    }

    scenario("find crmTemplates from CrmTemplatesId without questionId with locale") {
      Mockito
        .when(
          persistentCrmTemplatesService.find(
            ArgumentMatchers.eq(0),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(Some("locale"))
          )
        )
        .thenReturn(Future.successful(Seq(aCrmTemplates)))

      whenReady(crmTemplatesService.find(0, None, None, Some("locale")), Timeout(3.seconds)) { result =>
        result.head.crmTemplatesId shouldBe CrmTemplatesId("id")
      }
    }

    scenario("find crmTemplates from CrmTemplatesId without params") {
      Mockito
        .when(
          persistentCrmTemplatesService.find(
            ArgumentMatchers.eq(0),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None),
            ArgumentMatchers.eq(None)
          )
        )
        .thenReturn(Future.successful(Seq.empty))

      whenReady(crmTemplatesService.find(0, None, None, None), Timeout(3.seconds)) { result =>
        result.isEmpty shouldBe true
      }
    }
  }

  feature("count crmTemplates") {
    scenario("count crmTemplates from CrmTemplatesId with questionId ignore locale") {
      crmTemplatesService.count(Some(QuestionId("toto")), Some("locale-ignored"))

      Mockito.verify(persistentCrmTemplatesService).count(Some(QuestionId("toto")), None)
    }

    scenario("count crmTemplates from CrmTemplatesId without questionId with locale") {
      crmTemplatesService.count(None, Some("locale"))

      Mockito.verify(persistentCrmTemplatesService).count(None, Some("locale"))
    }

    scenario("count crmTemplates from CrmTemplatesId without params") {
      crmTemplatesService.count(None, None)

      Mockito.verify(persistentCrmTemplatesService).count(None, None)
    }
  }
}

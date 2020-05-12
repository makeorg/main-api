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

import org.make.api.MakeUnitTest
import org.make.api.technical.{IdGenerator, IdGeneratorComponent}
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.QuestionId
import org.mockito.Mockito
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class PartnerServiceTest
    extends MakeUnitTest
    with DefaultPartnerServiceComponent
    with PersistentPartnerServiceComponent
    with IdGeneratorComponent {

  override val persistentPartnerService: PersistentPartnerService = mock[PersistentPartnerService]
  override val idGenerator: IdGenerator = mock[IdGenerator]

  val partner: Partner = Partner(
    partnerId = PartnerId("partner"),
    name = "name",
    logo = Some("logo"),
    link = Some("link"),
    organisationId = None,
    partnerKind = PartnerKind.Founder,
    questionId = QuestionId("question"),
    weight = 20f
  )

  feature("create partner") {
    scenario("creation") {
      Mockito.when(idGenerator.nextPartnerId()).thenReturn(PartnerId("partner"))
      Mockito.when(persistentPartnerService.persist(partner)).thenReturn(Future.successful(partner))

      whenReady(
        partnerService.createPartner(request = CreatePartnerRequest(
          name = "name",
          logo = Some("logo"),
          link = Some("link"),
          organisationId = None,
          partnerKind = PartnerKind.Founder,
          questionId = QuestionId("question"),
          weight = 20f
        )
        ),
        Timeout(2.seconds)
      ) { partner =>
        partner.partnerId should be(PartnerId("partner"))
      }
    }
  }

  feature("update partner") {
    scenario("update when no partner is found") {
      Mockito.when(persistentPartnerService.getById(PartnerId("not-found"))).thenReturn(Future.successful(None))

      whenReady(
        partnerService.updatePartner(
          partnerId = PartnerId("not-found"),
          UpdatePartnerRequest(
            name = "name",
            logo = Some("logo"),
            link = Some("link"),
            organisationId = None,
            partnerKind = PartnerKind.Founder,
            weight = 20f
          )
        ),
        Timeout(2.seconds)
      ) { partner =>
        partner should be(None)
      }
    }

    scenario("update when partner is found") {
      val updatedPartner: Partner = partner.copy(name = "update-name")

      Mockito.when(persistentPartnerService.getById(PartnerId("partner"))).thenReturn(Future.successful(Some(partner)))
      Mockito
        .when(persistentPartnerService.modify(updatedPartner))
        .thenReturn(Future.successful(updatedPartner))

      whenReady(
        partnerService.updatePartner(
          partnerId = PartnerId("partner"),
          UpdatePartnerRequest(
            name = "update-name",
            logo = Some("logo"),
            link = Some("link"),
            organisationId = None,
            partnerKind = PartnerKind.Founder,
            weight = 20f
          )
        ),
        Timeout(2.seconds)
      ) { partner =>
        partner.map(_.name) should be(Some("update-name"))
      }
    }
  }

}

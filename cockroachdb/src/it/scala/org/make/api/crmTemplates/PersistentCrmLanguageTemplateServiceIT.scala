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

package org.make.api.crmTemplates

import org.make.api.DatabaseTest
import org.make.core.crmTemplate.{CrmLanguageTemplate, CrmLanguageTemplateId, CrmTemplateKind, TemplateId}
import org.make.core.reference.Language
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt

class PersistentCrmLanguageTemplateServiceIT
    extends DatabaseTest
    with DefaultPersistentCrmLanguageTemplateServiceComponent {

  Feature("CRUD crm language templates") {

    Scenario("insert templates") {
      val crmLanguageTemplate = CrmLanguageTemplate(
        id = CrmLanguageTemplateId("id"),
        kind = CrmTemplateKind.Registration,
        language = Language("FR"),
        template = TemplateId("1")
      )
      whenReady(persistentCrmLanguageTemplateService.persist(Seq(crmLanguageTemplate)), Timeout(2.seconds)) {
        _ should be(Seq(crmLanguageTemplate))
      }
    }

    Scenario("update and get") {

      val accepted = CrmLanguageTemplate(
        id = CrmLanguageTemplateId("toUpdate-1"),
        kind = CrmTemplateKind.ProposalAccepted,
        language = Language("FR"),
        template = TemplateId("2")
      )

      val refused = CrmLanguageTemplate(
        id = CrmLanguageTemplateId("toUpdate-2"),
        kind = CrmTemplateKind.ProposalRefused,
        language = Language("FR"),
        template = TemplateId("3")
      )

      val original = Seq(accepted, refused)
      val updated = Seq(
        accepted.copy(kind = CrmTemplateKind.B2BProposalAccepted, template = TemplateId("4")),
        refused.copy(kind = CrmTemplateKind.B2BProposalRefused, template = TemplateId("5"))
      )

      val steps = for {
        _      <- persistentCrmLanguageTemplateService.persist(original)
        result <- persistentCrmLanguageTemplateService.modify(updated)
      } yield result

      whenReady(steps, Timeout(2.seconds)) {
        _ should contain theSameElementsAs updated
      }

    }

    Scenario("list by language") {

      val belgian = CrmTemplateKind.values.zipWithIndex.map {
        case (kind, i) =>
          CrmLanguageTemplate(
            id = CrmLanguageTemplateId(s"belgian-$kind"),
            kind = kind,
            language = Language("BE"),
            template = TemplateId(i.toString)
          )
      }

      val steps = for {
        _               <- persistentCrmLanguageTemplateService.persist(belgian)
        frenchTemplates <- persistentCrmLanguageTemplateService.list(Language("FR"))
      } yield frenchTemplates.size

      whenReady(steps, Timeout(2.seconds)) {
        _ shouldBe 3
      }

    }

    Scenario("all") {
      whenReady(persistentCrmLanguageTemplateService.all(), Timeout(2.seconds)) { templates =>
        templates.count(_.language == Language("FR")) shouldBe 3
        templates.count(_.language == Language("BE")) shouldBe CrmTemplateKind.values.size
      }
    }

  }

}

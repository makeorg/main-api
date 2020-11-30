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
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.core.crmTemplate.{CrmQuestionTemplate, CrmQuestionTemplateId, CrmTemplateKind, TemplateId}
import org.make.core.question.QuestionId
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt

class PersistentCrmQuestionTemplateServiceIT
    extends DatabaseTest
    with DefaultPersistentCrmQuestionTemplateServiceComponent
    with DefaultPersistentQuestionServiceComponent {

  override protected val cockroachExposedPort: Int = 40023

  override def beforeAll(): Unit = {
    super.beforeAll()
    whenReady(persistentQuestionService.persist(question(id = QuestionId("foo"), slug = "foo")), Timeout(2.seconds)) {
      _ =>
        ()
    }
    whenReady(persistentQuestionService.persist(question(id = QuestionId("bar"), slug = "bar")), Timeout(2.seconds)) {
      _ =>
        ()
    }
  }

  private val crmLanguageTemplate = CrmQuestionTemplate(
    id = CrmQuestionTemplateId("id"),
    kind = CrmTemplateKind.Registration,
    questionId = QuestionId("foo"),
    template = TemplateId("1")
  )

  Feature("CRUD crm question templates") {

    Scenario("persist") {
      whenReady(persistentCrmQuestionTemplateService.persist(crmLanguageTemplate), Timeout(2.seconds)) {
        _ should be(crmLanguageTemplate)
      }
    }

    Scenario("get") {
      whenReady(persistentCrmQuestionTemplateService.get(crmLanguageTemplate.id), Timeout(2.seconds)) {
        _ shouldBe Some(crmLanguageTemplate)
      }
    }

    Scenario("modify") {

      val original = CrmQuestionTemplate(
        id = CrmQuestionTemplateId("toUpdate-1"),
        kind = CrmTemplateKind.ProposalAccepted,
        questionId = QuestionId("foo"),
        template = TemplateId("2")
      )

      val updated = original.copy(kind = CrmTemplateKind.B2BProposalAccepted, template = TemplateId("3"))

      val steps = for {
        _      <- persistentCrmQuestionTemplateService.persist(original)
        result <- persistentCrmQuestionTemplateService.modify(updated)
      } yield result

      whenReady(steps, Timeout(2.seconds)) {
        _ shouldBe updated
      }

    }

    Scenario("list by question") {

      val other = CrmQuestionTemplate(
        id = CrmQuestionTemplateId(s"other"),
        kind = CrmTemplateKind.ForgottenPassword,
        questionId = QuestionId("bar"),
        template = TemplateId("1")
      )

      val steps = for {
        _            <- persistentCrmQuestionTemplateService.persist(other)
        fooTemplates <- persistentCrmQuestionTemplateService.list(crmLanguageTemplate.questionId)
      } yield fooTemplates.size

      whenReady(steps, Timeout(2.seconds)) {
        _ shouldBe 2
      }

    }

    Scenario("remove") {

      val steps = for {
        _      <- persistentCrmQuestionTemplateService.remove(crmLanguageTemplate.id)
        result <- persistentCrmQuestionTemplateService.get(crmLanguageTemplate.id)
      } yield result

      whenReady(steps) {
        _ shouldBe None
      }

    }

  }

}

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

import org.make.api.DatabaseTest
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt

class PersistentPartnerServiceIT
    extends DatabaseTest
    with DefaultPersistentPartnerServiceComponent
    with DefaultPersistentQuestionServiceComponent {

  override protected val cockroachExposedPort: Int = 40014

  val partner: Partner = Partner(
    partnerId = PartnerId("partner"),
    name = "name",
    logo = Some("logo"),
    link = Some("link"),
    organisationId = None,
    partnerKind = PartnerKind.Founder,
    questionId = QuestionId("question"),
    weight = 20F
  )

  val question = Question(
    questionId = QuestionId("question"),
    slug = "question",
    country = Country("FR"),
    language = Language("fr"),
    question = "question ?",
    operationId = None,
    themeId = None
  )

  feature("get partner by id") {
    scenario("get existing partner") {
      val futurePartner = for {
        _       <- persistentQuestionService.persist(question)
        _       <- persistentPartnerService.persist(partner)
        partner <- persistentPartnerService.getById(PartnerId("partner"))
      } yield partner

      whenReady(futurePartner, Timeout(2.seconds)) { partner =>
        partner.map(_.name) should be(Some("name"))
      }
    }

    scenario("get non existing partner") {
      whenReady(persistentPartnerService.getById(PartnerId("not-found")), Timeout(2.seconds)) { partner =>
        partner should be(None)
      }
    }
  }

  feature("search partners") {
    scenario("search all") {
      val futurePartner = for {
        _ <- persistentPartnerService.persist(partner.copy(partnerId = PartnerId("partner2")))
        _ <- persistentPartnerService.persist(partner.copy(partnerId = PartnerId("partner3")))
        partners <- persistentPartnerService.find(
          questionId = None,
          organisationId = None,
          start = 0,
          end = None,
          sort = None,
          order = None
        )
      } yield partners

      whenReady(futurePartner, Timeout(2.seconds)) { partners =>
        partners.map(_.partnerId).contains(PartnerId("partner2")) should be(true)
      }
    }

    scenario("search by questionId") {
      val futurePartner = for {
        _ <- persistentQuestionService.persist(question.copy(questionId = QuestionId("question2"), slug = "question-2"))
        _ <- persistentPartnerService.persist(
          partner.copy(partnerId = PartnerId("partner4"), questionId = QuestionId("question2"))
        )
        _ <- persistentPartnerService.persist(
          partner.copy(partnerId = PartnerId("partner5"), questionId = QuestionId("question2"))
        )
        partners <- persistentPartnerService.find(
          questionId = Some(QuestionId("question2")),
          organisationId = None,
          start = 0,
          end = None,
          sort = None,
          order = None
        )
      } yield partners

      whenReady(futurePartner, Timeout(2.seconds)) { partners =>
        partners.map(_.partnerId).contains(PartnerId("partner4")) should be(true)
      }
    }
  }

  feature("count partners") {
    scenario("count by questionId") {

      val futurePartnerCount = for {
        _ <- persistentQuestionService.persist(
          question.copy(questionId = QuestionId("question-for-count-partner-scenario"), slug = "question-count-partner")
        )
        _ <- persistentPartnerService.persist(
          partner.copy(
            partnerId = PartnerId("partner-count-1"),
            questionId = QuestionId("question-for-count-partner-scenario")
          )
        )
        _ <- persistentPartnerService.persist(
          partner.copy(
            partnerId = PartnerId("partner-count-2"),
            questionId = QuestionId("question-for-count-partner-scenario")
          )
        )
        count <- persistentPartnerService.count(
          questionId = Some(QuestionId("question-for-count-partner-scenario")),
          organisationId = None
        )
      } yield count

      whenReady(futurePartnerCount, Timeout(2.seconds)) { count =>
        count should be(2)
      }
    }
  }

  feature("update partners") {
    scenario("update existing partner") {
      val updatedPartner =
        partner.copy(name = "updated name", partnerKind = PartnerKind.ActionPartner, weight = 42F)

      whenReady(persistentPartnerService.modify(updatedPartner), Timeout(2.seconds)) { partner =>
        partner.partnerId should be(PartnerId("partner"))
        partner.name should be("updated name")
        partner.partnerKind should be(PartnerKind.ActionPartner)
        partner.weight should be(42F)
      }
    }
  }

}

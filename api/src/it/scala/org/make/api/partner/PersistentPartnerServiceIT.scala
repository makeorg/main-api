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

import cats.data.NonEmptyList
import org.make.api.DatabaseTest
import org.make.api.question.DefaultPersistentQuestionServiceComponent
import org.make.core.partner.{Partner, PartnerId, PartnerKind}
import org.make.core.question.{Question, QuestionId}
import org.make.core.reference.{Country, Language}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.duration.DurationInt
import org.make.core.technical.Pagination.Start

class PersistentPartnerServiceIT
    extends DatabaseTest
    with DefaultPersistentPartnerServiceComponent
    with DefaultPersistentQuestionServiceComponent {

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

  val question = Question(
    questionId = QuestionId("question"),
    slug = "question",
    countries = NonEmptyList.of(Country("FR")),
    language = Language("fr"),
    question = "question ?",
    shortTitle = None,
    operationId = None
  )

  Feature("get partner by id") {
    Scenario("get existing partner") {
      val futurePartner = for {
        _       <- persistentQuestionService.persist(question)
        _       <- persistentPartnerService.persist(partner)
        partner <- persistentPartnerService.getById(PartnerId("partner"))
      } yield partner

      whenReady(futurePartner, Timeout(2.seconds)) { partner =>
        partner.map(_.name) should be(Some("name"))
      }
    }

    Scenario("get non existing partner") {
      whenReady(persistentPartnerService.getById(PartnerId("not-found")), Timeout(2.seconds)) { partner =>
        partner should be(None)
      }
    }
  }

  Feature("search partners") {
    Scenario("search all") {
      val futurePartner = for {
        _ <- persistentPartnerService.persist(partner.copy(partnerId = PartnerId("partner2")))
        _ <- persistentPartnerService.persist(partner.copy(partnerId = PartnerId("partner3")))
        partners <- persistentPartnerService.find(
          questionId = None,
          organisationId = None,
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          partnerKind = None
        )
      } yield partners

      whenReady(futurePartner, Timeout(2.seconds)) { partners =>
        partners.map(_.partnerId).contains(PartnerId("partner2")) should be(true)
      }
    }

    Scenario("search by questionId") {
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
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          partnerKind = None
        )
      } yield partners

      whenReady(futurePartner, Timeout(2.seconds)) { partners =>
        partners.map(_.partnerId).contains(PartnerId("partner4")) should be(true)
      }
    }

    Scenario("search by partnerKind") {
      val futurePartner = for {
        _ <- persistentPartnerService.persist(
          partner.copy(partnerId = PartnerId("partner6"), partnerKind = PartnerKind.Media)
        )
        _ <- persistentPartnerService.persist(
          partner.copy(partnerId = PartnerId("partner7"), partnerKind = PartnerKind.Media)
        )
        partners <- persistentPartnerService.find(
          questionId = None,
          organisationId = None,
          start = Start.zero,
          end = None,
          sort = None,
          order = None,
          partnerKind = Some(PartnerKind.Media)
        )
      } yield partners

      whenReady(futurePartner, Timeout(2.seconds)) { partners =>
        partners.size shouldBe 2
        partners.map(_.partnerId).contains(PartnerId("partner6")) should be(true)
        partners.map(_.partnerId).contains(PartnerId("partner7")) should be(true)
      }
    }
  }

  Feature("count partners") {
    Scenario("count by questionId") {

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
          organisationId = None,
          partnerKind = None
        )
      } yield count

      whenReady(futurePartnerCount, Timeout(2.seconds)) { count =>
        count should be(2)
      }
    }

    Scenario("count by partnerKind") {

      val futurePartnerCount = for {
        _ <- persistentPartnerService.persist(
          partner.copy(partnerId = PartnerId("partner-count-3"), partnerKind = PartnerKind.Actor)
        )
        _ <- persistentPartnerService.persist(
          partner.copy(partnerId = PartnerId("partner-count-4"), partnerKind = PartnerKind.Actor)
        )
        count <- persistentPartnerService.count(
          questionId = None,
          organisationId = None,
          partnerKind = Some(PartnerKind.Actor)
        )
      } yield count

      whenReady(futurePartnerCount, Timeout(2.seconds)) { count =>
        count should be(2)
      }
    }
  }

  Feature("update partners") {
    Scenario("update existing partner") {
      val updatedPartner =
        partner.copy(name = "updated name", partnerKind = PartnerKind.ActionPartner, weight = 42f)

      whenReady(persistentPartnerService.modify(updatedPartner), Timeout(2.seconds)) { partner =>
        partner.partnerId should be(PartnerId("partner"))
        partner.name should be("updated name")
        partner.partnerKind should be(PartnerKind.ActionPartner)
        partner.weight should be(42f)
      }
    }
  }

}

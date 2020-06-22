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

package org.make.core.operation

import java.time.ZonedDateTime

import eu.timepit.refined.auto._
import eu.timepit.refined.scalacheck.numeric._
import eu.timepit.refined.types.numeric.PosShort
import org.make.core.DateHelper
import org.make.core.operation.OperationOfQuestion.Status._
import org.make.core.technical.generator.{CustomGenerators, EntitiesGen}
import org.scalacheck.Arbitrary
import org.scalatest.{FeatureSpec, Matchers}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class OperationOfQuestionTest extends FeatureSpec with EntitiesGen with Matchers with ScalaCheckDrivenPropertyChecks {

  private implicit val arbOperationOfQuestion: Arbitrary[OperationOfQuestion] = Arbitrary(genOperationOfQuestion)
  private implicit val arbZonedDateTime: Arbitrary[ZonedDateTime] = Arbitrary(CustomGenerators.Time.zonedDateTime)

  feature("status") {

    scenario("with a start date in the future operation is upcoming") {
      forAll { (ooq: OperationOfQuestion, delta: PosShort) =>
        ooq.copy(startDate = Some(DateHelper.now().plusSeconds(delta.toLong))).status should be(Upcoming)
      }
    }

    scenario("with an end date in the past operation is finished") {
      forAll { (ooq: OperationOfQuestion, delta: PosShort, duration: PosShort) =>
        val now = DateHelper.now()
        ooq
          .copy(
            startDate = Some(now.minusSeconds(delta.toLong + duration.toLong)),
            endDate = Some(now.minusSeconds(delta.toLong))
          )
          .status should be(Finished)
      }
    }

    scenario("with start and end dates around current date operation is open") {
      forAll { (ooq: OperationOfQuestion, halfDuration: PosShort) =>
        val now = DateHelper.now()
        ooq
          .copy(
            startDate = Some(now.minusSeconds(halfDuration.toLong)),
            endDate = Some(now.plusSeconds(halfDuration.toLong))
          )
          .status should be(Open)
      }
    }

    scenario("without a start date operation cannot be upcoming") {
      forAll { (ooq: OperationOfQuestion, date: ZonedDateTime) =>
        ooq.copy(startDate = None, endDate = Some(date)).status shouldNot be(Upcoming)
      }
    }

    scenario("without an end date operation cannot be finished") {
      forAll { (ooq: OperationOfQuestion, date: ZonedDateTime) =>
        ooq.copy(startDate = Some(date), endDate = None).status shouldNot be(Finished)
      }
    }

    scenario("without start and end date operation is open") {
      forAll { ooq: OperationOfQuestion =>
        ooq.copy(startDate = None, endDate = None).status should be(Open)
      }
    }

  }

}

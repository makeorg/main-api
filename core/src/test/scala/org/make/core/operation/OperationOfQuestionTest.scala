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

import eu.timepit.refined.auto._
import eu.timepit.refined.scalacheck.numeric._
import eu.timepit.refined.types.numeric.PosShort
import org.make.core.{DateHelper, MakeUnitTest}
import org.make.core.operation.OperationOfQuestion.Status._
import org.make.core.technical.generator.EntitiesGen
import org.scalacheck.Arbitrary
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class OperationOfQuestionTest extends MakeUnitTest with EntitiesGen with ScalaCheckDrivenPropertyChecks {

  private implicit val arbOperationOfQuestion: Arbitrary[OperationOfQuestion] = Arbitrary(genOperationOfQuestion)

  Feature("status") {

    Scenario("with a start date in the future operation is upcoming") {
      forAll { (ooq: OperationOfQuestion, delta: PosShort) =>
        ooq.copy(startDate = DateHelper.now().plusSeconds(delta.toLong)).status should be(Upcoming)
      }
    }

    Scenario("with an end date in the past operation is finished") {
      forAll { (ooq: OperationOfQuestion, delta: PosShort, duration: PosShort) =>
        val now = DateHelper.now()
        ooq
          .copy(startDate = now.minusSeconds(delta.toLong + duration.toLong), endDate = now.minusSeconds(delta.toLong))
          .status should be(Finished)
      }
    }

    Scenario("with start and end dates around current date operation is open") {
      forAll { (ooq: OperationOfQuestion, halfDuration: PosShort) =>
        val now = DateHelper.now()
        ooq
          .copy(startDate = now.minusSeconds(halfDuration.toLong), endDate = now.plusSeconds(halfDuration.toLong))
          .status should be(Open)
      }
    }

  }

}

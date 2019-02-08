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

package org.make.core
import java.time.LocalDate

import org.scalatest.Matchers._
import org.scalatest.{FeatureSpec, _}

class ValidationTest extends FeatureSpec {
  feature("email validation") {
    val fieldName = "email"

    scenario("valid email") {
      val emailValue = "yopmail@make.org"
      def validation(): Unit = Validation.validate(Validation.validateEmail(fieldName, emailValue))

      Matchers.noException should be thrownBy validation()
    }
    scenario("invalid email") {
      val emailValue = "invalid"
      def validation(): Unit = Validation.validate(Validation.validateEmail(fieldName, emailValue))

      an[ValidationFailedError] should be thrownBy validation()
    }
    scenario("invalid email with space") {
      val emailValue = "invalid yopmail@make.org"
      def validation(): Unit = Validation.validate(Validation.validateEmail(fieldName, emailValue))

      an[ValidationFailedError] should be thrownBy validation()
    }
  }

  feature("user input validation") {
    val fieldName = "userInput"

    scenario("valid input") {
      val inputValue = "valid input"
      def validation(): Unit = Validation.validate(Validation.validateUserInput(fieldName, inputValue, None))

      Matchers.noException should be thrownBy validation()
    }

    scenario("invalid input") {
      val inputValue = "<a>invalid input</a>"
      def validation(): Unit = Validation.validate(Validation.validateUserInput(fieldName, inputValue, None))

      a[ValidationFailedError] should be thrownBy validation()
    }

    scenario("valid input with < / >") {
      val inputValue = "< & >"
      def validation(): Unit = Validation.validate(Validation.validateUserInput(fieldName, inputValue, None))

      Matchers.noException should be thrownBy validation()
    }
  }

  feature("age validation") {
    val fieldName = "dateOfBirth"

    scenario("valid datesOfBirth") {
      val aged13 = LocalDate.now.minusYears(13)
      def validation13(): Unit = Validation.validate(Validation.validateAge(fieldName, Some(aged13)))
      Matchers.noException should be thrownBy validation13()

      val aged14 = LocalDate.now.minusYears(14)
      def validation14(): Unit = Validation.validate(Validation.validateAge(fieldName, Some(aged14)))
      Matchers.noException should be thrownBy validation14()

      val aged119 = LocalDate.now.minusYears(119)
      def validation119(): Unit = Validation.validate(Validation.validateAge(fieldName, Some(aged119)))
      Matchers.noException should be thrownBy validation119()

      def validationNone(): Unit = Validation.validate(Validation.validateAge(fieldName, None))
      Matchers.noException should be thrownBy validationNone()
    }

    scenario("invalid datesOfBirth") {
      val aged12 = LocalDate.now.minusYears(12)
      def validation12(): Unit = Validation.validate(Validation.validateAge(fieldName, Some(aged12)))
      an[ValidationFailedError] should be thrownBy validation12()

      val aged120 = LocalDate.now.minusYears(120)
      def validation120(): Unit = Validation.validate(Validation.validateAge(fieldName, Some(aged120)))
      an[ValidationFailedError] should be thrownBy validation120()

      val aged121 = LocalDate.now.minusYears(121)
      def validation121(): Unit = Validation.validate(Validation.validateAge(fieldName, Some(aged121)))
      an[ValidationFailedError] should be thrownBy validation121()
    }

  }

}

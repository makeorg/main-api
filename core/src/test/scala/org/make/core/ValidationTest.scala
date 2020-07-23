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
      val aged7 = LocalDate.now.minusYears(7)
      def validation7(): Unit = Validation.validate(Validation.validateAge(fieldName, Some(aged7)))
      an[ValidationFailedError] should be thrownBy validation7()

      val aged120 = LocalDate.now.minusYears(120)
      def validation120(): Unit = Validation.validate(Validation.validateAge(fieldName, Some(aged120)))
      an[ValidationFailedError] should be thrownBy validation120()

      val aged121 = LocalDate.now.minusYears(121)
      def validation121(): Unit = Validation.validate(Validation.validateAge(fieldName, Some(aged121)))
      an[ValidationFailedError] should be thrownBy validation121()
    }

  }

  feature("legal consent validation") {
    val fieldName = "legal"

    scenario("valid") {
      val aged14 = LocalDate.now.minusYears(14)
      def validation14(): Unit = Validation.validate(Validation.validateLegalConsent(fieldName, aged14, Some(true)))
      Matchers.noException should be thrownBy validation14()

      val aged15 = LocalDate.now.minusYears(15)
      def validation15(): Unit = Validation.validate(Validation.validateLegalConsent(fieldName, aged15, None))
      Matchers.noException should be thrownBy validation15()

      val aged119 = LocalDate.now.minusYears(119)
      def validation119(): Unit = Validation.validate(Validation.validateLegalConsent(fieldName, aged119, None))
      Matchers.noException should be thrownBy validation119()

      val aged8 = LocalDate.now.minusYears(8)
      def validation8(): Unit = Validation.validate(Validation.validateLegalConsent(fieldName, aged8, Some(true)))
      Matchers.noException should be thrownBy validation8()
    }

    scenario("invalid") {
      val aged7 = LocalDate.now.minusYears(7)
      def validation7(): Unit = Validation.validate(Validation.validateLegalConsent(fieldName, aged7, Some(true)))
      an[ValidationFailedError] should be thrownBy validation7()

      val aged8 = LocalDate.now.minusYears(8)
      def validation8WithConsent(): Unit =
        Validation.validate(Validation.validateLegalConsent(fieldName, aged8, Some(false)))
      an[ValidationFailedError] should be thrownBy validation8WithConsent()

      def validation8WithoutConsent(): Unit =
        Validation.validate(Validation.validateLegalConsent(fieldName, aged8, None))
      an[ValidationFailedError] should be thrownBy validation8WithoutConsent()

      val aged14 = LocalDate.now.minusYears(14)
      def validation14WithConsent(): Unit =
        Validation.validate(Validation.validateLegalConsent(fieldName, aged14, Some(false)))
      an[ValidationFailedError] should be thrownBy validation14WithConsent()

      def validation14WithoutConsent(): Unit =
        Validation.validate(Validation.validateLegalConsent(fieldName, aged14, None))
      an[ValidationFailedError] should be thrownBy validation14WithoutConsent()

      val aged120 = LocalDate.now.minusYears(120)
      def validation120(): Unit = Validation.validate(Validation.validateLegalConsent(fieldName, aged120, Some(true)))
      an[ValidationFailedError] should be thrownBy validation120()

      val aged121 = LocalDate.now.minusYears(121)
      def validation121(): Unit = Validation.validate(Validation.validateLegalConsent(fieldName, aged121, None))
      an[ValidationFailedError] should be thrownBy validation121()
    }

  }

  feature("color validation") {
    val fieldName = "colorInput"
    scenario("valid color") {
      val color = "#424242"
      def validation(): Unit = Validation.validate(Validation.validateColor(fieldName, color, None))
      Matchers.noException should be thrownBy validation()
    }

    scenario("invalid color") {
      val color1 = "red"
      def validation1(): Unit = Validation.validate(Validation.validateColor(fieldName, color1, None))
      an[ValidationFailedError] should be thrownBy validation1()

      val color2 = "424242"
      def validation2(): Unit = Validation.validate(Validation.validateColor(fieldName, color2, None))
      an[ValidationFailedError] should be thrownBy validation2()

      val color3 = "#42AHOD"
      def validation3(): Unit = Validation.validate(Validation.validateColor(fieldName, color3, None))
      an[ValidationFailedError] should be thrownBy validation3()
    }
  }

}

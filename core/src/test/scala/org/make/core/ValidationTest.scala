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
}
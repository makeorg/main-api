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

package org.make.api.user.validation

import com.typesafe.config.{Config, ConfigFactory}
import org.make.api.MakeUnitTest
import org.make.api.user.UserRegisterData
import org.make.core.reference.{Country, Language}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DefaultUserRegistrationValidatorTest extends MakeUnitTest {

  val data = UserRegisterData(
    email = "test",
    firstName = None,
    password = None,
    lastIp = None,
    country = Country("FR"),
    language = Language("fr")
  )

  feature("email validation") {
    scenario("accept-all") {
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.defaultConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      whenReady(validator.canRegister(data), Timeout(2.seconds)) {
        _ should be(true)
      }
    }

    scenario("refuse-all") {
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.refusingConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      whenReady(validator.canRegister(data), Timeout(2.seconds)) {
        _ should be(false)
      }
    }

    scenario("mixed") {
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.mixedConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      whenReady(validator.canRegister(data), Timeout(2.seconds)) {
        _ should be(true)
      }
    }

    scenario("with-configuration") {
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.configuredConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      whenReady(validator.canRegister(data), Timeout(2.seconds)) {
        _ should be(true)
      }
    }
  }

}

object DefaultUserRegistrationValidatorTest {
  val defaultConfiguration: String =
    """
      |make-api.email-validation {
      |  validators {
      |    always-validate {
      |      validator-class = "org.make.api.user.validation.AlwaysAuthorize"
      |      parameters {
      |
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  val refusingConfiguration: String =
    """
      |make-api.email-validation  {
      |  validators {
      |    always-refuse {
      |      validator-class = "org.make.api.user.validation.AlwaysRefuse"
      |      parameters {
      |
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  val mixedConfiguration: String =
    """
      |make-api.email-validation  {
      |  validators {
      |    always-validate {
      |      validator-class = "org.make.api.user.validation.AlwaysAuthorize"
      |      parameters {
      |
      |      }
      |    }
      |    always-refuse {
      |      validator-class = "org.make.api.user.validation.AlwaysRefuse"
      |      parameters {
      |
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  val configuredConfiguration: String =
    """
      |make-api.email-validation  {
      |  validators {
      |    always-refuse {
      |      validator-class = "org.make.api.user.validation.ValidatorUsingParameters"
      |      parameters {
      |        can-register = true
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

}

class AlwaysRefuse extends EmailValidation {
  override def init(name: String, config: Config): Unit = {}
  override def canRegister(userData: UserRegisterData): Future[Boolean] = Future.successful(false)
}

class ValidatorUsingParameters extends EmailValidation {
  private var canRegister: Boolean = false

  override def init(name: String, config: Config): Unit = {
    canRegister = config.getBoolean("can-register")
  }
  override def canRegister(userData: UserRegisterData): Future[Boolean] = {
    Future.successful(canRegister)
  }
}

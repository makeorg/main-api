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

import java.time.LocalDate

import com.typesafe.config.{Config, ConfigFactory}
import org.make.api.MakeUnitTest
import org.make.api.user.{UserProfileRequestValidation, UserRegisterData}
import org.make.core.{Requirement, Validation, ValidationFailedError}
import org.make.core.reference.{Country, Language}
import org.scalatest.concurrent.PatienceConfiguration.Timeout

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class DefaultUserRegistrationValidatorTest extends MakeUnitTest {

  Feature("email validation") {
    val data = UserRegisterData(
      email = "test",
      firstName = None,
      password = None,
      lastIp = None,
      country = Country("FR"),
      language = Language("fr")
    )

    Scenario("accept-all") {
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.defaultConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      whenReady(validator.canRegister(data), Timeout(2.seconds)) {
        _ should be(true)
      }
    }

    Scenario("refuse-all") {
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.refusingAndLegalConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      whenReady(validator.canRegister(data), Timeout(2.seconds)) {
        _ should be(false)
      }
    }

    Scenario("mixed") {
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.mixedConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      whenReady(validator.canRegister(data), Timeout(2.seconds)) {
        _ should be(true)
      }
    }

    Scenario("with-configuration") {
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.configuredConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration) {
        override lazy val requirementsConfiguration: Seq[UserRequirement] = Seq.empty
      }
      whenReady(validator.canRegister(data), Timeout(2.seconds)) {
        _ should be(true)
      }
    }
  }

  Feature("requirements") {
    Scenario("empty") {
      val request = UserRequest(None, None, None)
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.defaultConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      noException should be thrownBy Validation.validate(validator.requirements(request): _*)
    }

    Scenario("legal valid") {
      val requestWithUnderage = UserRequest(Some(LocalDate.now().minusYears(9)), Some(true), Some(true))
      val requestWithAdult = UserRequest(Some(LocalDate.now().minusYears(9)), Some(true), Some(true))
      val requestWithoutAge = UserRequest(None, None, None)
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.refusingAndLegalConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      noException should be thrownBy Validation.validate(validator.requirements(requestWithUnderage): _*)
      noException should be thrownBy Validation.validate(validator.requirements(requestWithAdult): _*)
      noException should be thrownBy Validation.validate(validator.requirements(requestWithoutAge): _*)
    }

    Scenario("legal failed") {
      val requestWithUnderage = UserRequest(Some(LocalDate.now().minusYears(9)), Some(true), Some(false))
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.refusingAndLegalConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      a[ValidationFailedError] should be thrownBy Validation.validate(validator.requirements(requestWithUnderage): _*)
    }

    Scenario("mixed valid") {
      val request = UserRequest(Some(LocalDate.now().minusYears(9)), Some(true), Some(true))
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.mixedConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      noException should be thrownBy Validation.validate(validator.requirements(request): _*)
    }

    Scenario("mixed failed") {
      val requestWithoutAge = UserRequest(None, None, None)
      val requestWithAge = UserRequest(Some(LocalDate.now().minusYears(9)), None, None)
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.mixedConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration)
      a[ValidationFailedError] should be thrownBy Validation.validate(validator.requirements(requestWithoutAge): _*)
      a[ValidationFailedError] should be thrownBy Validation.validate(validator.requirements(requestWithAge): _*)
    }

    Scenario("configurable") {
      val request = UserRequest(Some(LocalDate.now().minusYears(9)), None, None)
      val requestFailed = UserRequest(Some(LocalDate.now().minusYears(50)), None, None)
      val configuration = ConfigFactory.parseString(DefaultUserRegistrationValidatorTest.configuredConfiguration)
      val validator = new DefaultUserRegistrationValidator(configuration) {
        override lazy val requirementsConfiguration: Seq[UserRequirement] =
          configuration.getString("requirements").split(",").toSeq.flatMap {
            case ConfigurableRequirement.value =>
              val param = configuration.getInt(s"${ConfigurableRequirement.value}.param")
              Some(ConfigurableRequirement(param))
          }
      }
      noException should be thrownBy Validation.validate(validator.requirements(request): _*)
      a[ValidationFailedError] should be thrownBy Validation.validate(validator.requirements(requestFailed): _*)
    }
  }
}

object DefaultUserRegistrationValidatorTest {
  val defaultConfiguration: String =
    """
      |email {
      | validators {
      |   always-validate {
      |     validator-class = "org.make.api.user.validation.AlwaysAuthorize"
      |     parameters {
      |
      |     }
      |   }
      | }
      |}
      |
      |requirements = ""
      |""".stripMargin

  val refusingAndLegalConfiguration: String =
    """
      |email {
      | validators {
      |   always-refuse {
      |     validator-class = "org.make.api.user.validation.AlwaysRefuse"
      |     parameters {
      |
      |     }
      |   }
      | }
      |}
      |
      |requirements = "underage-legal-consent"
      |""".stripMargin

  val mixedConfiguration: String =
    """
      |email {
      | validators {
      |   always-validate {
      |     validator-class = "org.make.api.user.validation.AlwaysAuthorize"
      |     parameters {
      |
      |     }
      |   }
      |   always-refuse {
      |     validator-class = "org.make.api.user.validation.AlwaysRefuse"
      |     parameters {
      |
      |     }
      |   }
      | }
      |}
      |
      |requirements = "age-is-required,underage-legal-consent"
      |""".stripMargin

  val configuredConfiguration: String =
    """
      |email {
      |  validators {
      |    always-refuse {
      |      validator-class = "org.make.api.user.validation.ValidatorUsingParameters"
      |      parameters {
      |        can-register = true
      |      }
      |    }
      |  }
      |}
      |
      |configurable-requirement {
      |  param = 42
      |}
      |
      |requirements = "configurable-requirement"
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

case class UserRequest(
  dateOfBirth: Option[LocalDate],
  legalMinorConsent: Option[Boolean],
  legalAdvisorApproval: Option[Boolean]
) extends UserProfileRequestValidation

case class ConfigurableRequirement(param: Int) extends UserRequirement {
  override def requirement(request: UserProfileRequestValidation): Seq[Requirement] =
    Seq(
      Validation.validateField(
        "dateOfBirth",
        "error",
        request.dateOfBirth.exists(_.isAfter(LocalDate.now.minusYears(42))),
        "message if error"
      )
    )
}

object ConfigurableRequirement {
  val value: String = "configurable-requirement"
}

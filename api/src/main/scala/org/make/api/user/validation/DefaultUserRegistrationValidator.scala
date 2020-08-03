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

import com.typesafe.config.{Config, ConfigObject}
import org.make.api.ActorSystemComponent
import org.make.api.user.{UserProfileRequestValidation, UserRegisterData}
import org.make.core.Requirement

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait UserRegistrationValidator {
  def canRegister(userData: UserRegisterData): Future[Boolean]
  def requirements(request: UserProfileRequestValidation): Seq[Requirement]
}

trait UserRegistrationValidatorComponent {
  def userRegistrationValidator: UserRegistrationValidator
}

trait DefaultUserRegistrationValidatorComponent extends UserRegistrationValidatorComponent {
  self: ActorSystemComponent =>

  override lazy val userRegistrationValidator: UserRegistrationValidator = new DefaultUserRegistrationValidator(
    actorSystem.settings.config.getConfig("make-api.user-validation")
  )
}

class DefaultUserRegistrationValidator(configuration: Config) extends UserRegistrationValidator {

  private val validatorsConfiguration: ConfigObject =
    configuration.getObject("email.validators")

  private var validators: Seq[EmailValidation] = Seq.empty

  validatorsConfiguration.keySet.forEach { name =>
    val validatorConfig = validatorsConfiguration.toConfig.getConfig(name)
    val extraParameters = validatorConfig.getConfig("parameters")
    val className = validatorConfig.getString("validator-class")

    val validator = Thread
      .currentThread()
      .getContextClassLoader
      .loadClass(className)
      .getConstructor()
      .newInstance()
      .asInstanceOf[EmailValidation]

    validator.init(name, extraParameters)

    validators :+= validator
  }

  override def canRegister(userData: UserRegisterData): Future[Boolean] = {
    Future
      .traverse(validators) { _.canRegister(userData) }
      .map(_.exists(identity))
  }

  lazy val requirementsConfiguration: Seq[UserRequirement] =
    configuration.getString("requirements").split(",").toSeq.flatMap {
      case AgeIsRequired.value => Some(AgeIsRequired)
      case LegalConsent.value  => Some(LegalConsent)
      case ""                  => None
    }

  override def requirements(request: UserProfileRequestValidation): Seq[Requirement] =
    requirementsConfiguration.flatMap(_.requirement(request))

}

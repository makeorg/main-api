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

package org.make.api.user.validation

import org.make.api.user.UserProfileRequestValidation
import org.make.core.Validation.validateLegalConsent
import org.make.core.{Requirement, Validation}

trait UserRequirement {
  def requirement(request: UserProfileRequestValidation): Seq[Requirement]
}

case object AgeIsRequired extends UserRequirement {
  val value: String = "age-is-required"

  override def requirement(request: UserProfileRequestValidation): Seq[Requirement] =
    Seq(Validation.mandatoryField("dateOfBirth", request.dateOfBirth))
}

case object LegalConsent extends UserRequirement {
  val value: String = "underage-legal-consent"

  override def requirement(request: UserProfileRequestValidation): Seq[Requirement] = {
    Seq(
      request.dateOfBirth.map(validateLegalConsent("legalMinorConsent", _, request.legalMinorConsent)),
      request.dateOfBirth.map(validateLegalConsent("legalAdvisorApproval", _, request.legalAdvisorApproval))
    ).flatten
  }
}

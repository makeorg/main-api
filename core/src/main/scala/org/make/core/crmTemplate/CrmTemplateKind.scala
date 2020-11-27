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

package org.make.core.crmTemplate

import enumeratum.{Enum, EnumEntry}

sealed abstract class CrmTemplateKind extends EnumEntry with Product with Serializable

object CrmTemplateKind extends Enum[CrmTemplateKind] {

  case object Registration extends CrmTemplateKind
  case object Welcome extends CrmTemplateKind
  case object ResendRegistration extends CrmTemplateKind
  case object ForgottenPassword extends CrmTemplateKind
  case object ProposalAccepted extends CrmTemplateKind
  case object ProposalRefused extends CrmTemplateKind

  case object B2BRegistration extends CrmTemplateKind
  case object B2BEmailChanged extends CrmTemplateKind
  case object B2BForgottenPassword extends CrmTemplateKind
  case object B2BProposalAccepted extends CrmTemplateKind
  case object B2BProposalRefused extends CrmTemplateKind

  override def values: IndexedSeq[CrmTemplateKind] = findValues

}

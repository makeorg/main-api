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

package org.make.api.migrations

import org.make.api.migrations.ProposalHelper.UserInfo
import org.make.core.reference.{Country, Language}

object CultureData extends InsertOperationFixtureData {
  override val operationSlug: String = CultureOperation.operationSlug
  override val country: Country = Country("FR")
  override val language: Language = Language("fr")
  override val users: Seq[UserInfo] = Seq(
    UserInfo("yopmail+leila@make.org", "Leila", 33, country, language),
    UserInfo("yopmail+ariane@make.org", "Ariane", 19, country, language),
    UserInfo("yopmail+aminata@make.org", "Aminata", 45, country, language),
    UserInfo("yopmail+josephine@make.org", "Joséphine", 54, country, language),
    UserInfo("yopmail+joao@make.org", "Joao", 48, country, language),
    UserInfo("yopmail+isaac@make.org", "Isaac", 38, country, language),
    UserInfo("yopmail+pierre-marie@make.org", "Pierre-Marie", 50, country, language),
    UserInfo("yopmail+chen@make.org", "Chen", 17, country, language),
    UserInfo("yopmail+lucas@make.org", "Lucas", 23, country, language),
    UserInfo("yopmail+elisabeth@make.org", "Elisabeth", 36, country, language),
    UserInfo("yopmail+jordi@make.org", "Jordi", 30, country, language),
    UserInfo("yopmail+sophie@make.org", "Sophie", 39, country, language),
    UserInfo("yopmail+alek@make.org", "Alek", 21, country, language),
    UserInfo("yopmail+elisabeth@make.org", "Elisabeth", 65, country, language),
    UserInfo("yopmail+lucas@make.org", "Lucas", 18, country, language)
  )
  override def dataResource: String = "fixtures/proposals_culture.csv"
  override val runInProduction: Boolean = false
}

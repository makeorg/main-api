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

object MIPIMGbData extends InsertOperationFixtureData {
  override val operationSlug: String = MIPIMOperation.operationSlug
  override val country: Country = Country("GB")
  override val language: Language = Language("en")
  override val users: Seq[UserInfo] = Seq(
    UserInfo("yopmail+Maélys@make.org", "Maélys", 19, country, language),
    UserInfo("yopmail+Robert@make.org", "Robert", 56, country, language),
    UserInfo("yopmail+Benoît@make.org", "Benoît", 35, country, language),
    UserInfo("yopmail+Rémy@make.org", "Rémy", 27, country, language),
    UserInfo("yopmail+Nadine@make.org", "Nadine", 47, country, language),
    UserInfo("yopmail+Lucile@make.org", "Lucile", 25, country, language),
    UserInfo("yopmail+Amina@make.org", "Amina", 40, country, language),
    UserInfo("yopmail+Sophia@make.org", "Sophia", 56, country, language),
    UserInfo("yopmail+Rolland@make.org", "Rolland", 41, country, language),
    UserInfo("yopmail+Dimitri@make.org", "Dimitri", 31, country, language),
    UserInfo("yopmail+Tom@make.org", "Tom", 18, country, language),
    UserInfo("yopmail+Lola@make.org", "Lola", 19, country, language),
    UserInfo("yopmail+Mohamed@make.org", "Mohamed", 41, country, language),
    UserInfo("yopmail+Typhaine@make.org", "Typhaine", 28, country, language),
    UserInfo("yopmail+Amandine@make.org", "Amandine", 39, country, language),
    UserInfo("yopmail+Tram@make.org", "Tram", 30, country, language),
    UserInfo("yopmail+Clara@make.org", "Clara", 23, country, language),
    UserInfo("yopmail+Agnès@make.org", "Agnès", 54, country, language),
    UserInfo("yopmail+Irène@make.org", "Irène", 62, country, language),
    UserInfo("yopmail+Philip@make.org", "Philip", 43, country, language),
  )
  override def dataResource: String = "fixtures/proposals_mipim-gb.csv"
  override val runInProduction: Boolean = true
}

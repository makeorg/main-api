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

object MIPIMFrData extends InsertOperationFixtureData {
  override val operationSlug: String = MIPIMOperation.operationSlug
  override val country: Country = Country("FR")
  override val language: Language = Language("fr")
  override val users: Seq[UserInfo] = Seq(
    UserInfo("yopmail+maelys@make.org", "Maélys", 19, country, language),
    UserInfo("yopmail+robert@make.org", "Robert", 56, country, language),
    UserInfo("yopmail+benoit@make.org", "Benoît", 35, country, language),
    UserInfo("yopmail+remy@make.org", "Rémy", 27, country, language),
    UserInfo("yopmail+nadine@make.org", "Nadine", 47, country, language),
    UserInfo("yopmail+lucile@make.org", "Lucile", 25, country, language),
    UserInfo("yopmail+amina@make.org", "Amina", 40, country, language),
    UserInfo("yopmail+sophia@make.org", "Sophia", 56, country, language),
    UserInfo("yopmail+rolland@make.org", "Rolland", 41, country, language),
    UserInfo("yopmail+dimitri@make.org", "Dimitri", 31, country, language),
    UserInfo("yopmail+tom@make.org", "Tom", 18, country, language),
    UserInfo("yopmail+lola@make.org", "Lola", 19, country, language),
    UserInfo("yopmail+mohamed@make.org", "Mohamed", 41, country, language),
    UserInfo("yopmail+typhaine@make.org", "Typhaine", 28, country, language),
    UserInfo("yopmail+amandine@make.org", "Amandine", 39, country, language),
    UserInfo("yopmail+tram@make.org", "Tram", 30, country, language),
    UserInfo("yopmail+clara@make.org", "Clara", 23, country, language),
    UserInfo("yopmail+agnes@make.org", "Agnès", 54, country, language),
    UserInfo("yopmail+irene@make.org", "Irène", 62, country, language),
    UserInfo("yopmail+philip@make.org", "Philip", 43, country, language),
  )
  override def dataResource: String = "fixtures/proposals_mipim-fr.csv"
  override val runInProduction: Boolean = true
}

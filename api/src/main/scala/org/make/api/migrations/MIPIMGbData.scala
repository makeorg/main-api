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
  override val operationSlug: String = MIPIMGbOperation.operationSlug
  override val country: Country = Country("GB")
  override val language: Language = Language("en")
  override val users: Seq[UserInfo] = Seq(
    UserInfo("yopmail+Samantha@make.org", "Samantha", 19, country, language),
    UserInfo("yopmail+Tyler @make.org", "Tyler ", 56, country, language),
    UserInfo("yopmail+Joshua@make.org", "Joshua", 35, country, language),
    UserInfo("yopmail+Nathan@make.org", "Nathan", 27, country, language),
    UserInfo("yopmail+Chelsea@make.org", "Chelsea", 47, country, language),
    UserInfo("yopmail+Emily@make.org", "Emily", 25, country, language),
    UserInfo("yopmail+Ashley@make.org", "Ashley", 40, country, language),
    UserInfo("yopmail+Elizabeth @make.org", "Elizabeth ", 56, country, language),
    UserInfo("yopmail+Matt@make.org", "Matt", 41, country, language),
    UserInfo("yopmail+Dimitri@make.org", "Dimitri", 31, country, language),
    UserInfo("yopmail+Steve@make.org", "Steve", 18, country, language),
    UserInfo("yopmail+Lily@make.org", "Lily", 19, country, language),
    UserInfo("yopmail+Bill@make.org", "Bill", 41, country, language),
    UserInfo("yopmail+Hannah@make.org", "Hannah", 28, country, language),
    UserInfo("yopmail+Lauren@make.org", "Lauren", 39, country, language),
    UserInfo("yopmail+Rachel@make.org", "Rachel", 30, country, language),
    UserInfo("yopmail+Megan@make.org", "Megan", 23, country, language),
    UserInfo("yopmail+Grace@make.org", "Grace", 54, country, language),
    UserInfo("yopmail+Victoria@make.org", "Victoria", 62, country, language),
    UserInfo("yopmail+Philip@make.org", "Philip", 43, country, language),
  )
  override def dataResource: String = "fixtures/proposals_mipim-gb.csv"
  override val runInProduction: Boolean = true
}

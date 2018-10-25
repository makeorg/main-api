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

object HdfData extends InsertOperationFixtureData {
  override val operationSlug: String = HdfOperation.operationSlug
  override val country: Country = Country("FR")
  override val language: Language = Language("fr")
  override val users: Seq[UserInfo] = Seq(
    UserInfo("yopmail+marie@make.org", "Marie", 18, country, language),
    UserInfo("yopmail+michele@make.org", "Michèle", 24, country, language),
    UserInfo("yopmail+kevin@make.org", "Kévin", 30, country, language),
    UserInfo("yopmail+raissa@make.org", "Raïssa", 15, country, language),
    UserInfo("yopmail+nicolas@make.org", "Nicolas", 23, country, language),
    UserInfo("yopmail+celine@make.org", "Céline", 17, country, language),
    UserInfo("yopmail+amine@make.org", "Amine", 16, country, language),
    UserInfo("yopmail+alexandra@make.org", "Alexandra", 25, country, language),
    UserInfo("yopmail+loris@make.org", "Loris", 26, country, language),
    UserInfo("yopmail+karine@make.org", "Karine", 29, country, language),
    UserInfo("yopmail+matteo@make.org", "Mattéo", 10, country, language),
    UserInfo("yopmail+solene@make.org", "Soplène", 21, country, language),
  )
  override def dataResource: String = "fixtures/proposals_jeunes_hdf.csv"
  override val runInProduction: Boolean = true
}

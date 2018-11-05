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

object DITPdata extends InsertOperationFixtureData {
  override val operationSlug: String = DITPOperation.operationSlug
  override val country: Country = Country("FR")
  override val language: Language = Language("fr")
  override val users: Seq[UserInfo] = Seq(
    UserInfo("yopmail+marie@make.org", "Marie", 18, country, language),
    UserInfo("yopmail+michèle@make.org", "Michèle", 69, country, language),
    UserInfo("yopmail+pierre@make.org", "Pierre", 67, country, language),
    UserInfo("yopmail+kévin@make.org", "Kévin", 31, country, language),
    UserInfo("yopmail+raïssa@make.org", "Raïssa", 24, country, language),
    UserInfo("yopmail+nicolas@make.org", "Nicolas", 48, country, language),
    UserInfo("yopmail+céline@make.org", "Céline", 51, country, language),
    UserInfo("yopmail+amine@make.org", "Amine", 59, country, language),
    UserInfo("yopmail+alexandra@make.org", "Alexandra", 37, country, language),
    UserInfo("yopmail+loris@make.org", "Loris", 21, country, language),
    UserInfo("yopmail+karine@make.org", "Karine", 56, country, language),
    UserInfo("yopmail+mattéo@make.org", "Mattéo", 28, country, language),
  )
  override def dataResource: String = "fixtures/proposals_ditp.csv"
  override val runInProduction: Boolean = true

}

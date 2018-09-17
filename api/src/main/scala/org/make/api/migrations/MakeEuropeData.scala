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

object MakeEuropeData extends InsertOperationFixtureData {
  override val operationSlug: String = MakeEuropeOperation.operationSlug
  override val country: Country = Country("GB")
  override val language: Language = Language("en")

  override val users: Seq[UserInfo] = Seq(
    UserInfo("yopmail+harry@make.org", "Harry", 38, country, language),
    UserInfo("yopmail+hazel@make.org", "Hazel", 19, country, language),
    UserInfo("yopmail+jack@make.org", "Jack", 27, country, language),
    UserInfo("yopmail+george@make.org", "George", 54, country, language),
    UserInfo("yopmail+abby@make.org", "Abby", 28, country, language)
  )
  override def dataResource: String = "fixtures/proposals_make-europe.csv"
  override val runInProduction: Boolean = false
}

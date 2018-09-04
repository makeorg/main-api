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

object ChanceAuxJeunesData extends InsertOperationFixtureData {
  override val operationSlug: String = ChanceAuxJeunesOperation.operationSlug
  override val country: Country = Country("FR")
  override val language: Language = Language("fr")
  override val users: Seq[UserInfo] = {
    Seq(
      UserInfo("yopmail+sandrine@make.org", "Sandrine", 35, country, language),
      UserInfo("yopmail+corinne@make.org", "Corinne", 52, country, language),
      UserInfo("yopmail+julie@make.org", "Julie", 18, country, language),
      UserInfo("yopmail+lionel@make.org", "Lionel", 25, country, language),
      UserInfo("yopmail+jean@make.org", "Jean", 48, country, language),
      UserInfo("yopmail+odile@make.org", "Odile", 29, country, language),
      UserInfo("yopmail+nicolas@make.org", "Nicolas", 42, country, language),
      UserInfo("yopmail+jamel@make.org", "Jamel", 22, country, language),
      UserInfo("yopmail+laurene@make.org", "Laurène", 27, country, language),
      UserInfo("yopmail+françois@make.org", "François", 33, country, language),
      UserInfo("yopmail+aissatou@make.org", "Aissatou", 31, country, language),
      UserInfo("yopmail+eric@make.org", "Éric", 56, country, language),
      UserInfo("yopmail+sylvain@make.org", "Sylvain", 46, country, language)
    )
  }
  override def dataResource: String = "fixtures/proposals_chance-aux-jeunes.csv"
  override val runInProduction: Boolean = false
}

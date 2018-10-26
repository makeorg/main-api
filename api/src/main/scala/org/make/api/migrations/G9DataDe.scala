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

object G9DataDe extends InsertOperationFixtureData {
  override val operationSlug: String = G9Operation.operationSlug
  override val country: Country = Country("DE")
  override val language: Language = Language("de")
  override val users: Seq[UserInfo] = Seq(
    UserInfo("yopmail+beate@make.org", "Beate", 39, country, language),
    UserInfo("yopmail+sebastian@make.org", "Sebastian", 35, country, language),
    UserInfo("yopmail+jonas@make.org", "Jonas", 27, country, language),
    UserInfo("yopmail+christian@make.org", "Christian", 47, country, language),
    UserInfo("yopmail+julia@make.org", "Julia", 40, country, language),
    UserInfo("yopmail+asja@make.org", "Asja", 56, country, language),
    UserInfo("yopmail+luca@make.org", "Luca", 41, country, language),
    UserInfo("yopmail+eva@make.org", "Eva", 31, country, language),
    UserInfo("yopmail+anja@make.org", "Anja", 19, country, language),
    UserInfo("yopmail+andrea@make.org", "Andrea", 43, country, language),
    UserInfo("yopmail+tomas@make.org", "Tomas", 18, country, language),
    UserInfo("yopmail+alexander@make.org", "Alexander", 69, country, language),
    UserInfo("yopmail+martin@make.org", "Martin", 67, country, language),
    UserInfo("yopmail+nils@make.org", "Nils", 31, country, language),
    UserInfo("yopmail+ida@make.org", "Ida", 24, country, language),
    UserInfo("yopmail+khatarina@make.org", "Khatarina", 48, country, language),
    UserInfo("yopmail+daniela@make.org", "Daniela", 51, country, language),
  )
  override def dataResource: String = "fixtures/proposals_g9-de.csv"
  override val runInProduction: Boolean = true
}

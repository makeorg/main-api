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

object HuffingPostV2Proposals extends MultiOperationsProposalHelper {

  override def users: Seq[UserInfo] =
    Seq(
      UserInfo("yopmail+maelys@make.org", "Maélys", 19, Country("FR"), Language("fr")),
      UserInfo("yopmail+robert@make.org", "Robert", 56, Country("FR"), Language("fr")),
      UserInfo("yopmail+benoit@make.org", "Benoît", 35, Country("FR"), Language("fr")),
      UserInfo("yopmail+remy@make.org", "Rémy", 27, Country("FR"), Language("fr")),
      UserInfo("yopmail+nadine@make.org", "Nadine", 47, Country("FR"), Language("fr")),
      UserInfo("yopmail+lucile@make.org", "Lucile", 25, Country("FR"), Language("fr")),
      UserInfo("yopmail+amina@make.org", "Amina", 40, Country("FR"), Language("fr")),
      UserInfo("yopmail+sophia@make.org", "Sophia", 56, Country("FR"), Language("fr")),
      UserInfo("yopmail+rolland@make.org", "Rolland", 64, Country("FR"), Language("fr")),
      UserInfo("yopmail+dimitri@make.org", "Dimitri", 31, Country("FR"), Language("fr")),
      UserInfo("yopmail+tom@make.org", "Tom", 18, Country("FR"), Language("fr")),
      UserInfo("yopmail+lola@make.org", "Lola", 19, Country("FR"), Language("fr")),
      UserInfo("yopmail+mohamed@make.org", "Mohamed", 41, Country("FR"), Language("fr")),
      UserInfo("yopmail+typhaine@make.org", "Typhaine", 28, Country("FR"), Language("fr")),
      UserInfo("yopmail+amandine@make.org", "Amandine", 39, Country("FR"), Language("fr")),
      UserInfo("yopmail+tram@make.org", "Tram", 30, Country("FR"), Language("fr")),
      UserInfo("yopmail+clara@make.org", "Clara", 46, Country("FR"), Language("fr")),
      UserInfo("yopmail+agnes@make.org", "Agnès", 54, Country("FR"), Language("fr")),
      UserInfo("yopmail+irene@make.org", "Irène", 68, Country("FR"), Language("fr")),
      UserInfo("yopmail+philip@make.org", "Philip", 43, Country("FR"), Language("fr")),
      UserInfo("yopmail+norman@make.org", "Norman", 24, Country("FR"), Language("fr")),
      UserInfo("yopmail+emilie@make.org", "Émilie", 32, Country("FR"), Language("fr")),
      UserInfo("yopmail+violette@make.org", "Violette", 21, Country("FR"), Language("fr")),
      UserInfo("yopmail+david@make.org", "David", 40, Country("FR"), Language("fr")),
      UserInfo("yopmail+lionel@make.org", "Lionel", 32, Country("FR"), Language("fr")),
      UserInfo("yopmail+yasmine@make.org", "Yasmine", 52, Country("FR"), Language("fr")),
      UserInfo("yopmail+youssef@make.org", "Youssef", 17, Country("FR"), Language("fr")),
      UserInfo("yopmail+oussama@make.org", "Oussama", 22, Country("FR"), Language("fr")),
      UserInfo("yopmail+rachel@make.org", "Rachel", 57, Country("FR"), Language("fr")),
      UserInfo("yopmail+sacha@make.org", "Sacha", 26, Country("FR"), Language("fr")),
      UserInfo("yopmail+gregory@make.org", "Grégory", 45, Country("FR"), Language("fr")),
      UserInfo("yopmail+alice@make.org", "Alice", 61, Country("FR"), Language("fr")),
      UserInfo("yopmail+yann@make.org", "Yann", 58, Country("FR"), Language("fr")),
      UserInfo("yopmail+gwennaelle@make.org", "Gwennaëlle", 56, Country("FR"), Language("fr")),
      UserInfo("yopmail+jessica@make.org", "Jessica", 39, Country("FR"), Language("fr")),
      UserInfo("yopmail+corentin@make.org", "Corentin", 47, Country("FR"), Language("fr")),
      UserInfo("yopmail+mario@make.org", "Mario", 78, Country("FR"), Language("fr")),
      UserInfo("yopmail+louis@make.org", "Louis", 16, Country("FR"), Language("fr")),
    )

  override def operationsProposalsSource: Map[String, String] = Map(
    "education-huffpost" -> "fixtures/huffingpost/proposals_education_v2.csv",
    "societe-huffpost" -> "fixtures/huffingpost/proposals_societe_v2.csv",
    "politique-huffpost" -> "fixtures/huffingpost/proposals_politique_v2.csv"
  )

  override def runInProduction: Boolean = true
}

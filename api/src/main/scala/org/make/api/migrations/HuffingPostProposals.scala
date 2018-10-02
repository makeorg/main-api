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

object HuffingPostProposals extends MultiOperationsProposalHelper {

  override def users: Seq[UserInfo] =
    Seq(
      UserInfo("yopmail+leila@make.org", "Leila", 33, Country("FR"), Language("fr")),
      UserInfo("yopmail+ariane@make.org", "Ariane", 19, Country("FR"), Language("fr")),
      UserInfo("yopmail+aminata@make.org", "Aminata", 45, Country("FR"), Language("fr")),
      UserInfo("yopmail+josephine@make.org", "Joséphine", 54, Country("FR"), Language("fr")),
      UserInfo("yopmail+joao@make.org", "Joao", 48, Country("FR"), Language("fr")),
      UserInfo("yopmail+isaac@make.org", "Isaac", 38, Country("FR"), Language("fr")),
      UserInfo("yopmail+pierre-marie@make.org", "Pierre-Marie", 50, Country("FR"), Language("fr")),
      UserInfo("yopmail+chen@make.org", "Chen", 17, Country("FR"), Language("fr")),
      UserInfo("yopmail+lucas@make.org", "Lucas", 23, Country("FR"), Language("fr")),
      UserInfo("yopmail+elisabeth@make.org", "Elisabeth", 36, Country("FR"), Language("fr")),
      UserInfo("yopmail+jordi@make.org", "Jordi", 30, Country("FR"), Language("fr")),
      UserInfo("yopmail+sophie@make.org", "Sophie", 39, Country("FR"), Language("fr")),
      UserInfo("yopmail+alek@make.org", "Alek", 21, Country("FR"), Language("fr")),
      UserInfo("yopmail+elisabeth@make.org", "Elisabeth", 65, Country("FR"), Language("fr")),
      UserInfo("yopmail+lucas@make.org", "Lucas", 18, Country("FR"), Language("fr")),
      UserInfo("yopmail+sandrine@make.org", "Sandrine", 35, Country("FR"), Language("fr")),
      UserInfo("yopmail+corinne@make.org", "Corinne", 52, Country("FR"), Language("fr")),
      UserInfo("yopmail+julie@make.org", "Julie", 18, Country("FR"), Language("fr")),
      UserInfo("yopmail+lionel@make.org", "Lionel", 25, Country("FR"), Language("fr")),
      UserInfo("yopmail+jean@make.org", "Jean", 48, Country("FR"), Language("fr")),
      UserInfo("yopmail+odile@make.org", "Odile", 29, Country("FR"), Language("fr")),
      UserInfo("yopmail+nicolas@make.org", "Nicolas", 42, Country("FR"), Language("fr")),
      UserInfo("yopmail+jamel@make.org", "Jamel", 22, Country("FR"), Language("fr")),
      UserInfo("yopmail+laurene@make.org", "Laurène", 27, Country("FR"), Language("fr")),
      UserInfo("yopmail+françois@make.org", "François", 33, Country("FR"), Language("fr")),
      UserInfo("yopmail+aissatou@make.org", "Aissatou", 31, Country("FR"), Language("fr")),
      UserInfo("yopmail+eric@make.org", "Éric", 56, Country("FR"), Language("fr")),
      UserInfo("yopmail+sylvain@make.org", "Sylvain", 46, Country("FR"), Language("fr"))
    )

  def operationsProposalsSource: Map[String, String] = Map(
    "politique-huffpost" -> "fixtures/huffingpost/proposals_politique.csv",
    "economie-huffpost" -> "fixtures/huffingpost/proposals_economie.csv",
    "international-huffpost" -> "fixtures/huffingpost/proposals_international.csv",
    "culture-huffpost" -> "fixtures/huffingpost/proposals_culture.csv",
    "ecologie-huffpost" -> "fixtures/huffingpost/proposals_ecologie.csv",
    "societe-huffpost" -> "fixtures/huffingpost/proposals_societe.csv",
    "education-huffpost" -> "fixtures/huffingpost/proposals_education.csv"
  )

  override def runInProduction: Boolean = false
}

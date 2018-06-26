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

package org.make.fixtures

import io.gatling.core.Predef._
import io.gatling.core.feeder.{Record, RecordSeqFeederBuilder}
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef.{jsonPath, status}
import org.make.fixtures.Proposal.ideaProposalFeederPath

object Idea {
  val ideaProposalFeeder: RecordSeqFeederBuilder[Any] = ssv(ideaProposalFeederPath, '"', '\\').records
  val proposalsByIdea: Map[String, IndexedSeq[Record[String]]] =
    ssv(ideaProposalFeederPath, '"', '\\').records.groupBy(_("ideaName"))
}

object IdeaChainBuilder extends SimulationConfig {
  private val createdStatus = 201
  private val statusOk = 200

  val createIdea: ChainBuilder = {
    exec(
      MakeServicesBuilder.createIdeaBuilder
        .body(ElFileBody("jsonModel/createIdea.json"))
        .asJSON
        .check(jsonPath("$.ideaId").saveAs("ideaId"))
        .check(status.is(createdStatus))
    )
  }
}

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

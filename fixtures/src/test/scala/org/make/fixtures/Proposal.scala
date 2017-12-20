package org.make.fixtures

import io.gatling.core.Predef._
import io.gatling.core.feeder._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef.{jsonPath, status}

object Proposal extends SimulationConfig {

  val proposalsByUsername: Map[String, IndexedSeq[Record[String]]] =
    ssv(proposalFeederPath, '"', '\\').records.groupBy { record =>
      record("username")
    }

  val vffProposalsByUsername: Map[String, IndexedSeq[Record[String]]] =
    ssv(vffProposalFeederPath, '"', '\\').records.groupBy { record =>
      record("username")
    }
}

object ProposalChainBuilder {
  private val createdStatus = 201
  private val statusOk = 200

  val createProposal: ChainBuilder = {
    exec(
      MakeServicesBuilder.createProposalBuilder
        .header("x-make-theme-id", "${theme}")
        .body(ElFileBody("jsonModel/createProposal.json"))
        .asJSON
        .check(jsonPath("$.proposalId").saveAs("proposalId"))
        .check(status.is(createdStatus))
    )
  }

  val createProposalVFF: ChainBuilder = {
    exec(
      MakeServicesBuilder.createProposalBuilder
        .body(ElFileBody("jsonModel/createProposal.json"))
        .asJSON
        .check(jsonPath("$.proposalId").saveAs("proposalId"))
        .check(status.is(createdStatus))
    )
  }

  val acceptProposal: ChainBuilder = {
    exec(
      MakeServicesBuilder.acceptProposalBuilder
        .body(ElFileBody("jsonModel/validateProposal.json"))
        .check(status.is(statusOk))
    )
  }

  val acceptProposalVFF: ChainBuilder = {
    exec(
      MakeServicesBuilder.acceptProposalBuilder
        .body(ElFileBody("jsonModel/validateProposalVFF.json"))
        .check(status.is(statusOk))
    )
  }

  val addProposalIdea: ChainBuilder = {
    exec(
      MakeServicesBuilder.addProposalToIdeaBuilder
        .body(ElFileBody("jsonModel/addProposalIdea.json"))
        .check(status.is(statusOk))
    )
  }
}

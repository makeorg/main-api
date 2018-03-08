package org.make.fixtures

import io.gatling.core.Predef._
import io.gatling.core.feeder._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef.{jsonPath, status}

object Proposal extends SimulationConfig {

  def groupProposalsByUsername(path: String): Map[String, IndexedSeq[Record[String]]] = {
    ssv(path, '"', '\\').records.groupBy { record =>
      record("username")
    }
  }

  val proposalsByUsername: Map[String, IndexedSeq[Record[String]]] =
    ssv(proposalFeederPath, '"', '\\').records.groupBy { record =>
      record("username")
    }

  val vffProposalsByUsername: Map[String, IndexedSeq[Record[String]]] =
    ssv(vffProposalFeederPath, '"', '\\').records.groupBy { record =>
      record("username")
    }

  val vffGBProposalsByUsername: Map[String, IndexedSeq[Record[String]]] = {
    ssv(vffGBProposalFeederPath, '"', '\\').records.groupBy { record =>
      record("username")
    }
  }

  val vffITProposalsByUsername: Map[String, IndexedSeq[Record[String]]] = {
    ssv(vffITProposalFeederPath, '"', '\\').records.groupBy { record =>
      record("username")
    }
  }

  val cpProposalsByUsername: Map[String, IndexedSeq[Record[String]]] =
    ssv(cpProposalFeederPath, '"', '\\').records.groupBy { record =>
      record("username")
    }

  val lpaeProposalsByUsername: Map[String, IndexedSeq[Record[String]]] =
    ssv(lpaeProposalFeederPath, '"', '\\').records.groupBy { record =>
      record("username")
    }

  val mveProposalsByUsername: Map[String, IndexedSeq[Record[String]]] =
    ssv(mveProposalFeederPath, '"', '\\').records.groupBy { record =>
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

  val createProposalOperation: ChainBuilder = {
    exec(
      MakeServicesBuilder.createProposalBuilder
        .header("x-make-operation", "${operationId}")
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

  val acceptProposalOperation: ChainBuilder = {
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

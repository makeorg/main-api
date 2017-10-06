package org.make.fixtures

import java.util.concurrent.ThreadLocalRandom

import io.gatling.core.Predef._
import io.gatling.core.feeder._
import io.gatling.core.json.Json
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef.{http, jsonPath, status}
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.util.{Failure, Success, Try}

object Proposal extends SimulationConfig {

  val maxClients = 3
  val httpConf: HttpProtocolBuilder = http
    .baseURL(baseURL)
    .acceptHeader("*/*")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader(defaultAcceptLanguage)
    .userAgentHeader(defaultUserAgent)
    .header("x-make-theme-id", "1234-5678-abcd")
    .header("x-make-operation", "")
    .header("x-make-source", "core")
    .header("x-make-location", "homepage")
    .header("x-make-question", "")
    .disableCaching

  val proposalsByUsername: Map[String, IndexedSeq[Record[String]]] =
    ssv(proposalFeederPath, '"', '\\').records.groupBy { record =>
      record("username")
    }

  private val userFeeder = ssv(userFeederPath, '"', '\\')
  private val defaultPause = 2

  val scnRegister: ScenarioBuilder = scenario("Create proposal with theme")
    .feed(userFeeder.circular)
    .exec(
      session =>
        session("username").validate[String].map { username: String =>
          val mayBeProposals: Option[IndexedSeq[Record[String]]] = Try(Some(proposalsByUsername(username))) match {
            case Success(indexedSeqOption) => indexedSeqOption
            case Failure(_)                => None
          }

          mayBeProposals.map { proposals =>
            proposals(ThreadLocalRandom.current.nextInt(proposals.length))
            val selectedProposal = proposals(ThreadLocalRandom.current.nextInt(proposals.length))
            val tags = Json.stringify(selectedProposal("tags").split('|').toSeq)
            session
              .set("content", selectedProposal("content"))
              .set("theme", selectedProposal("theme"))
              .set("tags", tags)
          }.getOrElse(session)
      }
    )
    .doIf(session => session.attributes.get("content").nonEmpty) {
      exec(
        UserChainBuilder
          .authenticate(UserAuthParams(username = "${username}", password = "${password}"))
          .pause(defaultPause),
        ProposalChainBuilder.createProposal
          .pause(defaultPause),
        UserChainBuilder.authenticateAsAdmin,
        ProposalChainBuilder.acceptProposal
      )
    }
}

object ProposalChainBuilder {
  private val createdStatus = 201
  private val statusOk = 200

  val createProposal: ChainBuilder = {

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
}

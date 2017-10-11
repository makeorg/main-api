package org.make.fixtures

import java.util.concurrent.ThreadLocalRandom

import io.gatling.core.Predef._
import io.gatling.core.feeder.Record
import io.gatling.core.json.Json
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.Proposal.proposalsByUsername
import org.make.fixtures.User._

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class Main extends Simulation {
  val maxClients = 710
  val httpConf: HttpProtocolBuilder = http
    .baseURL(baseURL)
    .acceptHeader("*/*")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader(defaultAcceptLanguage)
    .userAgentHeader(defaultUserAgent)
    .header("x-make-operation", "")
    .header("x-make-source", "core")
    .header("x-make-location", "homepage")
    .header("x-make-question", "")
    .disableCaching

  setUp(
    scenario("Register user and create proposal")
      .feed(User.userFeeder)
      .exec(UserChainBuilder.createUser)
      .exec(UserChainBuilder.authenticate(UserAuthParams(username = "${username}", password = "${password}")))
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
          ProposalChainBuilder.createProposal,
          UserChainBuilder.authenticateAsAdmin,
          ProposalChainBuilder.acceptProposal
        )
      }
      .inject(heavisideUsers(maxClients).over(15.minutes))
      .protocols(httpConf)
  )
}

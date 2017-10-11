package org.make.fixtures
import java.util.concurrent.ThreadLocalRandom
import io.gatling.core.feeder.Record
import io.gatling.core.json.Json
import io.gatling.core.Predef._
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.User._
import org.make.fixtures.Proposal.proposalsByUsername

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class Main extends Simulation {
  val maxClients = 326
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
      .exec(
        session =>
          session("username").validate[String].map { username: String =>
            val mayBeProposals: Option[IndexedSeq[Record[String]]] = Try(Some(proposalsByUsername(username))) match {
              case Success(indexedSeqOption) => indexedSeqOption
              case Failure(_)                => None
            }

            mayBeProposals.map { proposals =>
              session.set("proposals", proposals)
            }.getOrElse(session)
        }
      )
      .foreach("${proposals}", "proposal") {

        exec(session => {

          val proposal = session("proposal").as[Record[String]]
          val tags = Json.stringify(proposal("tags").split('|').toSeq)
          session
            .set("content", proposal("content"))
            .set("theme", proposal("theme"))
            .set("tags", tags)

        }).exec(
          UserChainBuilder.authenticate(UserAuthParams(username = "${username}", password = "${password}")),
          ProposalChainBuilder.createProposal,
          UserChainBuilder.authenticateAsAdmin,
          ProposalChainBuilder.acceptProposal
        )
      }
      .inject(heavisideUsers(maxClients).over(15.minutes))
      .protocols(httpConf)
  )
}

package org.make.fixtures

import io.gatling.core.Predef._
import io.gatling.core.feeder.Record
import io.gatling.core.json.Json
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.Proposal.vffProposalsByUsername
import org.make.fixtures.User._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class Vff extends Simulation {
  val maxClients = 10
  val httpConf: HttpProtocolBuilder = http
    .baseURL(baseURL)
    .acceptHeader("*/*")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader(defaultAcceptLanguage)
    .userAgentHeader(defaultUserAgent)
    .header("x-make-operation", "vff")
    .header("x-make-source", "core")
    .header("x-make-location", "")
    .header("x-make-question", "")
    .disableCaching

  setUp(
    scenario("Register Vff user and create proposal")
      .feed(User.vffUserFeeder)
      .exec(UserChainBuilder.createUser)
      .exec(
        session =>
          session("username").validate[String].map { username: String =>
            val mayBeProposals: Option[IndexedSeq[Record[String]]] =
              Try(Some(vffProposalsByUsername(username))) match {
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
            .set("tags", tags)
            .set("labels", Json.stringify(Seq.empty))

        }).exec(
          UserChainBuilder.authenticate(UserAuthParams(username = "${username}", password = "${password}")),
          ProposalChainBuilder.createProposalVFF,
          UserChainBuilder.authenticateAsAdmin,
          ProposalChainBuilder.acceptProposalVFF
        )
      }
      .inject(heavisideUsers(maxClients).over(2.minutes))
      .protocols(httpConf)
  )
}

package org.make.fixtures

import io.gatling.core.Predef._
import io.gatling.core.feeder.Record
import io.gatling.http.Predef.{http, jsonPath}
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.Proposal.lpaeProposalsByUsername
import org.make.fixtures.User._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class Lpae extends Simulation {
  val maxClients = 12
  val httpConf: HttpProtocolBuilder = http
    .baseURL(baseURL)
    .acceptHeader("*/*")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader(defaultAcceptLanguage)
    .userAgentHeader(defaultUserAgent)
    .header("x-make-operation", "lpae")
    .header("x-make-source", "core")
    .header("x-make-location", "")
    .header("x-make-question", "Vous avez les cl&eacute;s du monde, que changez-vous ?")
    .disableCaching

  setUp(
    scenario("Register Lpae user and create proposal")
      .feed(User.lpaeUserFeeder)
      .exec(UserChainBuilder.createUser)
      .exec(
        session =>
          session("username").validate[String].map { username: String =>
            val maybeProposals: Option[IndexedSeq[Record[String]]] =
              Try(Some(lpaeProposalsByUsername(username))) match {
                case Success(indexedSeqOption) => indexedSeqOption
                case Failure(_)                => None
              }

            maybeProposals.map { proposals =>
              session.set("proposals", proposals)
            }.getOrElse(session)
        }
      )
      .exec(
        MakeServicesBuilder
          .searchOperationBuilder("lpae")
          .asJSON
          .check(jsonPath("$[0].operationId").saveAs("operationId"))
      )
      .foreach("${proposals}", "proposal") {

        exec(session => {

          val proposal = session("proposal").as[Record[String]]

          session
            .set("content", proposal("content"))

        }).exec(
          UserChainBuilder.authenticate(UserAuthParams(username = "${username}", password = "${password}")),
          ProposalChainBuilder.createProposalOperation
        )
      }
      .inject(heavisideUsers(maxClients).over(2.minutes))
      .protocols(httpConf)
  )
}

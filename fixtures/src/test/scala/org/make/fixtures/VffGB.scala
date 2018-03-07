package org.make.fixtures

import io.gatling.core.Predef._
import io.gatling.core.feeder.Record
import io.gatling.http.Predef.{http, jsonPath}
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.Proposal.vffGBProposalsByUsername
import org.make.fixtures.User._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class VffGB extends Simulation {
  val maxClients = 18
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
    .header("x-make-country", "GB")
    .header("x-make-language", "en")
    .disableCaching

  setUp(
    scenario("Register VffGB users and create proposals")
      .feed(User.vffGBUserFeeder)
      .exec(UserChainBuilder.createUser)
      .exec(
        session =>
          session("username").validate[String].map { username: String =>
            val mayBeProposals: Option[IndexedSeq[Record[String]]] =
              Try(Some(vffGBProposalsByUsername(username))) match {
                case Success(indexedSeqOption) => indexedSeqOption
                case Failure(_)                => None
              }

            mayBeProposals.map { proposals =>
              session.set("proposals", proposals)
            }.getOrElse(session)
        }
      )
      .exec(
        MakeServicesBuilder
          .searchOperationBuilder("vff")
          .asJSON
          .check(jsonPath("$[0].operationId").saveAs("operationId"))
      )
      .foreach("${proposals}", "proposal") {

        exec(session => {

          val proposal = session("proposal").as[Record[String]]

          session
            .set("content", proposal("content"))
            .set("country", proposal("country"))
            .set("language", proposal("language"))

        }).exec(
          UserChainBuilder.authenticate(UserAuthParams(username = "${username}", password = "${password}")),
          ProposalChainBuilder.createProposalOperation
        )
      }
      .inject(heavisideUsers(maxClients).over(1.minutes))
      .protocols(httpConf)
  )
}

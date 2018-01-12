package org.make.fixtures

import io.gatling.core.Predef._
import io.gatling.core.feeder.Record
import io.gatling.core.json.Json
import io.gatling.http.Predef.{http, jsonPath}
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.Proposal.cpProposalsByUsername
import org.make.fixtures.User._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class ClimatParis extends Simulation {
  val maxClients = 1
  val httpConf: HttpProtocolBuilder = http
    .baseURL(baseURL)
    .acceptHeader("*/*")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader(defaultAcceptLanguage)
    .userAgentHeader(defaultUserAgent)
    .header("x-make-source", "core")
    .header("x-make-location", "")
    .header("x-make-question", "")
    .disableCaching

  setUp(
    scenario("Register CP user and create proposals")
      .exec(session => {
        session
          .set("username", "yopmail+mairie-paris-cp@make.org")
          .set("dateOfBirth", "null")
          .set("firstName", "Mairie de Paris")
          .set("password", "U93XCRtH")

      })
      .exec(UserChainBuilder.createUser)
      .exec(
        session =>
          session("username").validate[String].map { username: String =>
            val mayBeProposals: Option[IndexedSeq[Record[String]]] =
              Try(Some(cpProposalsByUsername(username))) match {
                case Success(indexedSeqOption) => indexedSeqOption
                case Failure(_)                => None
              }

            mayBeProposals.map { proposals =>
              session.set("proposals", proposals)
            }.getOrElse(session)
        }
      )
      .exec(
        MakeServicesBuilder.searchOperationBuilder("climatparis")
          .asJSON
          .check(jsonPath("$[0].operationId").saveAs("operationId"))

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
          ProposalChainBuilder.createProposalOperation,
          UserChainBuilder.authenticateAsAdmin
        )
      }
      .inject(heavisideUsers(maxClients).over(2.minutes))
      .protocols(httpConf)
  )
}
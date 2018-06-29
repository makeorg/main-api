/*
 *  Make.org Core API
 *  Copyright (C) 2018 Make.org
 *
 * This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.make.fixtures
import io.gatling.core.Predef._
import io.gatling.core.feeder.Record
import io.gatling.core.json.Json
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.Proposal.proposalsByUsername
import org.make.fixtures.User._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class Core extends Simulation {
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
    .header("x-make-country", "FR")
    .header("x-make-language", "fr")
    .disableCaching

  setUp(
    scenario("Register user and create proposal")
      .feed(User.userFeeder)
      .exec(UserChainBuilder.createUser)
      .exec(
        session =>
          session("username").validate[String].map { username: String =>
            val mayBeProposals: Option[IndexedSeq[Record[Any]]] = Try(Some(proposalsByUsername(username))) match {
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
          val labels = Json.stringify(proposal("labels").split('|').toSeq)
          val theme = {
            if (proposal("theme").isEmpty) {
              null
            } else {
              Json.stringify(proposal("theme"), isRootObject = false)
            }
          }

          session
            .set("content", proposal("content"))
            .set("theme", theme)
            .set("tags", tags)
            .set("labels", labels)
            .set("country", proposal("country"))
            .set("language", proposal("language"))
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

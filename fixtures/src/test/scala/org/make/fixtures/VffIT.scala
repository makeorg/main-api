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
import io.gatling.http.Predef.{http, jsonPath}
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.Proposal.vffITProposalsByUsername
import org.make.fixtures.User._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class VffIT extends Simulation {
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
    .header("x-make-country", "IT")
    .header("x-make-language", "it")
    .disableCaching

  setUp(
    scenario("Register VffIT users and create proposals")
      .feed(User.vffITUserFeeder)
      .exec(UserChainBuilder.createUser)
      .exec(
        session =>
          session("username").validate[String].map { username: String =>
            val mayBeProposals: Option[IndexedSeq[Record[String]]] =
              Try(Some(vffITProposalsByUsername(username))) match {
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

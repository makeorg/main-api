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

package org.make.migrations

import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.http.Predef.http
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.User._
import org.make.fixtures._

import scala.concurrent.duration.DurationInt

class Dec17_SimilarProposalsToIdea extends Simulation {
  val maxClients = 1
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
    scenario("Create idea and set idea to proposals")
      .exec(UserChainBuilder.authenticateAsAdmin)
      .exec(_.set("ideas", Idea.proposalsByIdea.keys.toSeq))
      .foreach("${ideas}", "idea") {
        exec { session =>
          val idea = session("idea").as[String]
          session.set("ideaName", idea)
        }.exec(IdeaChainBuilder.createIdea)
          .exec { session =>
            session("ideaName").validate[String].map { ideaName =>
              Idea.proposalsByIdea
                .get(ideaName)
                .map { proposalsIds =>
                  session.set("proposalsIds", proposalsIds.map(_("proposalId")))
                }
                .getOrElse(session)
            }
          }
          .foreach("${proposalsIds}", "proposalId") {
            exec(ProposalChainBuilder.addProposalIdea)
          }
      }
      .inject(heavisideUsers(maxClients).over(15.minutes))
      .protocols(httpConf)
  )
}

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
      .exec(_.set("ideas", Idea.ideas))
      .foreach("${ideas}", "idea")(exec { session =>
        val idea = session("idea").as[String]
        session.set("ideaName", idea)
      }.exec(IdeaChainBuilder.createIdea))
      .feed(Idea.ideaProposalFeeder)
      .exec(ProposalChainBuilder.addProposalIdea)
      .inject(heavisideUsers(maxClients).over(15.minutes))
      .protocols(httpConf)
  )
}

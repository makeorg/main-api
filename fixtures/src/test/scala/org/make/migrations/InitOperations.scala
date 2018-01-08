package org.make.migrations


import io.gatling.core.Predef._
import io.gatling.core.scenario.Simulation
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.User._
import org.make.fixtures.{MakeServicesBuilder, UserChainBuilder}

import scala.concurrent.duration.DurationInt

class InitOperations extends Simulation{
  val maxClients = 1
  val httpConf: HttpProtocolBuilder = http
    .baseURL(baseURL)
    .acceptHeader("*/*")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader(defaultAcceptLanguage)
    .userAgentHeader(defaultUserAgent)
    .header("x-make-operation", "")
    .header("x-make-source", "")
    .header("x-make-location", "")
    .header("x-make-question", "")
    .disableCaching

  val vffScenario: ScenarioBuilder = scenario("create Vff operation")
    .exec(UserChainBuilder.authenticateAsAdmin)
    .exec(
      MakeServicesBuilder.searchSequenceBuilder
        .body(StringBody("""{"tagIds": [], "themeIds": [], "slug": "comment-lutter-contre-les-violences-faites-aux-femmes", "sorts": []}"""))
        .asJSON
        .check(jsonPath("$.results[0].id").saveAs("sequenceId"))

    )
    .exec(session => {
      session
        .set("operationTitle", "Stop aux violences faites aux femmes")
        .set("operationSlug", "vff")

    })
    .exec(
      MakeServicesBuilder.createOperationBuilder
        .body(ElFileBody("jsonModel/createOperation.json"))
        .asJSON
        .check(status.is(201))
    )

  val climatParisScenario: ScenarioBuilder = scenario("create Climat Paris operation")
    .exec(UserChainBuilder.authenticateAsAdmin)
    .exec(
      MakeServicesBuilder.createSequenceBuilder
        .body(StringBody("""{"title": "Comment lutter contre le changement climatique à Paris ?", "themeIds": [], "tagIds": [], "searchable": false}"""))
        .asJSON
        .check(jsonPath("$.sequenceId").saveAs("sequenceId"))

    )
    .exec(session => {
      session
        .set("operationTitle", "Climat Paris")
        .set("operationSlug", "climatparis")

    })
    .exec(
      MakeServicesBuilder.createOperationBuilder
        .body(ElFileBody("jsonModel/createOperation.json"))
        .asJSON
        .check(status.is(201))
    )

  setUp(
    vffScenario.inject(heavisideUsers(maxClients).over(5.minutes)),
    climatParisScenario.inject(heavisideUsers(maxClients).over(5.minutes))
  ).protocols(httpConf)

}
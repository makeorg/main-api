package org.make.migrations

import io.gatling.core.Predef._
import io.gatling.core.feeder.Record
import io.gatling.core.json.Json
import io.gatling.http.Predef.{http, jsonPath, status}
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.Proposal.groupProposalsByUsername
import org.make.fixtures.User.{baseURL, defaultAcceptLanguage, defaultUserAgent}
import org.make.fixtures._

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

class Mar8_MveOperation extends Simulation {
  val maxClientsOperation = 1
  val maxClientsProposals = 12
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

  setUp(
    scenario("create mve operation")
      .exec(UserChainBuilder.authenticateAsAdmin)
      .exec(
        MakeServicesBuilder.searchSequenceBuilder
          .body(
            StringBody(
              """{"tagIds": [], "themeIds": [], "slug": "comment-mieux-vivre-ensemble", "sorts": []}"""
            )
          )
          .asJSON
          .check(jsonPath("$.results[0].id").saveAs("sequenceId"))
      )
      .doIf(session => !session.contains("sequenceId") || session("sequenceId").as[String].isEmpty){
        exec(
          MakeServicesBuilder.createSequenceBuilder
            .body(
              StringBody(
                """{"title": "Comment mieux vivre ensemble ?", "themeIds": [], "tagIds": [], "searchable": false}"""
              )
            )
            .asJSON
            .check(jsonPath("$.sequenceId").saveAs("sequenceId"))
        )
        .exec(
          MakeServicesBuilder.activateSequenceBuilder(sequenceId = "${sequenceId}")
            .body(
              StringBody(
                """{"status": "Published"}"""
              )
            )
        )
      }
      .exec(session => {
        session
          .set("operationSlug", "mieux-vivre-ensemble")
          .set("operationStatus", "Active")
          .set(
            "operationTranslations",
            Json.stringify(
              Array(
                Map("language" -> "fr", "title" -> "Mieux Vivre Ensemble")
              )
            )
          )
          .set(
            "operationCountryConfigurations",
            Json.stringify(
              Array(
                Map(
                  "countryCode" -> "FR",
                  "landingSequenceId" -> session("sequenceId").as[String],
                  "tagIds" ->
                    Seq(
                      "curation",
                      "prevention",
                      "action-associations",
                      "action-syndicats",
                      "action-entreprises",
                      "action-publique",
                      "cible-citadins",
                      "cible-ruraux",
                      "cible-jeunes",
                      "cible-personnes-agees",
                      "cible-associations",
                      "cible-syndicats",
                      "cible-entreprises",
                      "cible-individus",
                      "cible-elus",
                      "cible-collectivites-territoriales",
                      "cible-etats-gouvernements",
                      "numerique",
                      "engagement-associatif",
                      "partage",
                      "participation-citoyenne",
                      "effort-individuel",
                      "rse",
                      "fiscalite",
                      "aides-subventions",
                      "sanctions",
                      "couverture-sociale",
                      "regulation",
                      "sensibilisation",
                      "education",
                      "laicite",
                      "civisme",
                      "sport",
                      "culture",
                      "solidarites",
                      "sans-abri",
                      "pauvrete",
                      "mixite-sociale",
                      "discriminations",
                      "dialogue",
                      "intergenerationnel",
                      "vieillissement",
                      "jeunesse",
                      "handicap",
                      "urbanisme",
                      "ruralite",
                      "fracture-numerique",
                      "isolement"
                    )
                )
              )
            )
          )
      })
      .exec(
        MakeServicesBuilder.createOperationBuilder
          .body(ElFileBody("jsonModel/createOperation.json"))
          .asJSON
          .check(status.is(201))
      )
      .inject(heavisideUsers(maxClientsOperation).over(2.minutes))
      .protocols(httpConf),
    scenario("Register Mve user and create proposal")
      .feed(User.loadUserFeeder(User.mveUserFeederPath))
      .exec(UserChainBuilder.createUser)
      .exec(
        session =>
          session("username").validate[String].map { username: String =>
            val maybeProposals: Option[IndexedSeq[Record[String]]] =
              Try(Some(groupProposalsByUsername(path = User.mveProposalFeederPath)(username))) match {
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
          .searchOperationBuilder("mieux-vivre-ensemble")
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
      .inject(nothingFor(2.minutes), heavisideUsers(maxClientsProposals).over(2.minutes))
      .protocols(httpConf)
  ).protocols(httpConf)
}

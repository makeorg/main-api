package org.make.migrations

import io.gatling.core.Predef._
import io.gatling.core.json.Json
import io.gatling.core.scenario.Simulation
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef.{http, jsonPath, status}
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.User.{baseURL, defaultAcceptLanguage, defaultUserAgent}
import org.make.fixtures._

import scala.concurrent.duration.DurationInt
class Jan18_UpdateOperationMultiCountry extends Simulation {
  val maxClients = 1
  val httpConf: HttpProtocolBuilder = http
    .baseURL(baseURL)
    .acceptHeader("*/*")
    .acceptEncodingHeader("gzip, deflate")
    .acceptLanguageHeader(defaultAcceptLanguage)
    .userAgentHeader(defaultUserAgent)
    .header("x-make-operation", "")
    .header("x-make-source", "core")
    .header("x-make-location", "")
    .header("x-make-question", "")
    .header("x-make-country", "FR")
    .header("x-make-language", "fr")
    .disableCaching

  /*
   * feed VFF sequences UK and IT
   * get sequence Id vff to session.vffSequenceId
   * get sequence Id climatParis to session.climatParisSequenceId
   * create countryconfigurations
      **/

  val vffScenario: ScenarioBuilder = scenario("update Vff operation to set multi country landing sequences")
    .exec(UserChainBuilder.authenticateAsAdmin)
    .exec(
      MakeServicesBuilder.searchSequenceBuilder
        .body(
          StringBody(
            """{"tagIds": [], "themeIds": [], "slug": "comment-lutter-contre-les-violences-faites-aux-femmes", "sorts": []}"""
          )
        )
        .asJSON
        .check(jsonPath("$.results[0].id").saveAs("sequenceIdFR"))
    )
    .exec(
      MakeServicesBuilder.createSequenceBuilder
        .body(
          StringBody(
            """{"title": "How to combat violence against women?", "themeIds": [], "tagIds": [], "searchable": false}"""
          )
        )
        .asJSON
        .check(jsonPath("$.sequenceId").saveAs("sequenceIdEN"))
    )
    .exec(
      MakeServicesBuilder.createSequenceBuilder
        .body(
          StringBody(
            """{"title": "Come far fronte alla violenza sulle donne?", "themeIds": [], "tagIds": [], "searchable": false}"""
          )
        )
        .asJSON
        .check(jsonPath("$.sequenceId").saveAs("sequenceIdIT"))
    )
    .exec(session => {
      session
        .set("operationId", "vff")
        .set("operationSlug", "vff")
        .set("operationStatus", "Active")
        .set(
          "operationTranslations",
          Json.stringify(
            Array(
              Map("language" -> "fr", "title" -> "Stop aux violences faites aux femmes"),
              Map("language" -> "en", "title" -> "Stop violence against women"),
              Map("language" -> "it", "title" -> "Stop alla violenza sulle donne")
            )
          )
        )
        .set(
          "operationCountryConfigurations",
          Json.stringify(
            Array(
              Map(
                "countryCode" -> "FR",
                "landingSequenceId" -> session("sequenceIdFR").as[String],
                "tagIds" ->
                  Seq(
                    "signalement",
                    "police-justice",
                    "education-sensibilisation",
                    "image-des-femmes",
                    "independance-financiere",
                    "soutien-psychologique",
                    "hebergement",
                    "transports",
                    "monde-du-travail",
                    "monde-medical",
                    "agissements-sexistes",
                    "violences-sexuelles",
                    "harcelement",
                    "agressions-physiques",
                    "violences-conjugales",
                    "traditions-nefastes-mutilations",
                    "action-publique",
                    "prevention",
                    "protection",
                    "reponses"
                  )
              ),
              Map(
                "countryCode" -> "GB",
                "landingSequenceId" -> session("sequenceIdEN").as[String],
                "tagIds" ->
                  Seq(
                    "description",
                    "police-justice",
                    "professional-environment",
                    "transportation",
                    "public-action",
                    "accommodation",
                    "education-awareness",
                    "psychological-support",
                    "financial-independence",
                    "sexist-behaviour",
                    "mutilations",
                    "sexual-violence",
                    "harassment",
                    "harmful-questionnable-traditions",
                    "image-of-women",
                    "domestic-violence",
                    "prevention-en",
                    "protection",
                    "responses",
                    "aggression"
                  )
              ),
              Map(
                "countryCode" -> "IT",
                "landingSequenceId" -> session("sequenceIdIT").as[String],
                "tagIds" ->
                  Seq(
                    "avviso",
                    "polizia-giustizia",
                    "mondo-del-lavoro",
                    "trasporti",
                    "azione-pubblica",
                    "sistemazione",
                    "educazione-sensibilizzazione",
                    "sostegno-psicologico",
                    "indipendenza-finanziaria",
                    "comportamento-sessista",
                    "mutilazioni",
                    "violenze-sessuali",
                    "molestia",
                    "tradizioni-dannose",
                    "immagine-della-donna",
                    "violenza-coniugale",
                    "prevenzione",
                    "protezione",
                    "risposte",
                    "aggressione"
                  )
              )
            )
          )
        )
    })
    .exec(
      MakeServicesBuilder.updateOperationBuilder
        .body(ElFileBody("jsonModel/updateOperation.json"))
        .asJSON
        .check(status.is(200))
    )

  val climatParisScenario: ScenarioBuilder = scenario("update Climat Paris operation")
    .exec(UserChainBuilder.authenticateAsAdmin)
    .exec(
      MakeServicesBuilder
        .searchSimpleOperationBuilder("climatparis")
        .asJSON
        .check(jsonPath("$[0].operationId").saveAs("operationId"))
    )
    .exec(
      MakeServicesBuilder.searchSequenceBuilder
        .body(
          StringBody(
            """{"tagIds": [], "themeIds": [], "status":"Unpublished", "slug": "comment-lutter-contre-le-changement-climatique-a-paris", "sorts": []}"""
          )
        )
        .asJSON
        .check(jsonPath("$.results[0].id").saveAs("sequenceId"))
    )
    .exec(session => {
      session
        .set("operationSlug", "climatparis")
        .set("operationStatus", "Active")
        .set("operationTranslations", Json.stringify(Array(Map("language" -> "fr", "title" -> "Climat Paris"))))
        .set(
          "operationCountryConfigurations",
          Json.stringify(
            Array(
              Map(
                "countryCode" -> "FR",
                "landingSequenceId" -> session("sequenceId").as[String],
                "tagIds" ->
                  Seq(
                    "pollution",
                    "entreprises-emploi",
                    "qualite-de-vie",
                    "alimentation",
                    "energies-renouvelables",
                    "bio",
                    "sante",
                    "agriculture",
                    "circuits-courts",
                    "recyclage-zero-dechets",
                    "consommation-responsable",
                    "energies-traditionnelles",
                    "nouvelles-technologies",
                    "urbanisme-habitat",
                    "transports",
                    "fiscalite-subventions",
                    "sensibilisation-education",
                    "solidarite",
                    "action-publique",
                    "participation-citoyenne"
                  )
              )
            )
          )
        )
    })
    .exec(
      MakeServicesBuilder.updateOperationBuilder
        .body(ElFileBody("jsonModel/updateOperation.json"))
        .asJSON
        .check(status.is(200))
    )

  setUp(
    vffScenario.inject(heavisideUsers(maxClients).over(5.minutes)),
    climatParisScenario.inject(heavisideUsers(maxClients).over(5.minutes))
  ).protocols(httpConf)
}

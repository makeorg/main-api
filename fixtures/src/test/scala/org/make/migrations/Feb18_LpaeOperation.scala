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
import io.gatling.core.json.Json
import io.gatling.http.Predef.{http, jsonPath, status}
import io.gatling.http.protocol.HttpProtocolBuilder
import org.make.fixtures.User.{baseURL, defaultAcceptLanguage, defaultUserAgent}
import org.make.fixtures.{MakeServicesBuilder, UserChainBuilder}

import scala.concurrent.duration.DurationInt

class Feb18_LpaeOperation extends Simulation {
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

  setUp(
    scenario("create lpae operation")
      .exec(UserChainBuilder.authenticateAsAdmin)
      .exec(
        MakeServicesBuilder.createSequenceBuilder
          .body(
            StringBody(
              """{"title": "Vous avez les clés du monde, que changez-vous ?", "themeIds": [], "tagIds": [], "searchable": false}"""
            )
          )
          .asJSON
          .check(jsonPath("$.sequenceId").saveAs("sequenceId"))
      )
      .exec(session => {
        session
          .set("operationTitle", "La Parole aux Etudiants")
          .set("operationSlug", "lpae")
          .set(
            "tags",
            Json.stringify(
              Seq(
                "lpae-prevention",
                "lpae-repression",
                "lpae-curation",
                "lpae-democratie",
                "lpae-efficacite-de-l-etat",
                "lpae-ecologie",
                "lpae-agriculture",
                "lpae-pauvrete",
                "lpae-chomage",
                "lpae-conditions-de-travail",
                "lpae-finance",
                "lpae-entreprenariat",
                "lpae-recherche-innovation",
                "lpae-bouleversements-technologiques",
                "lpae-jeunesse",
                "lpae-inegalites",
                "lpae-discriminations",
                "lpae-culture",
                "lpae-vieillissement",
                "lpae-handicap",
                "lpae-logement",
                "lpae-sante",
                "lpae-solidarites",
                "lpae-justice",
                "lpae-sécurite",
                "lpae-terrorisme",
                "lpae-developpement",
                "lpae-guerres",
                "lpae-ue",
                "lpae-gouvernance-mondiale",
                "lpae-conquete-spatiale",
                "lpae-politique-eco-regulation",
                "lpae-aides-subventions",
                "lpae-fiscalite",
                "lpae-politique-monetaire",
                "lpae-couverture-sociale",
                "lpae-medical",
                "lpae-mobilites",
                "lpae-sensibilisation",
                "lpae-education",
                "lpae-sanctions",
                "lpae-rse",
                "lpae-nouvelles-technologies",
                "lpae-fonctionnement-des-institutions",
                "lpae-participation-citoyenne",
                "lpae-cible-etats-gouvernements",
                "lpae-cible-collectivites-territoriales",
                "lpae-cible-elus",
                "lpae-cible-individus",
                "lpae-cible-entreprises",
                "lpae-cible-syndicats",
                "lpae-cible-associations-ong",
                "lpae-cible-instances-mondiales",
                "lpae-action-publique",
                "lpae-action-des-individus",
                "lpae-action-entreprises",
                "lpae-action-syndicats",
                "lpae-action-associations-ong",
                "lpae-action-instances-mondiales"
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
      .inject(heavisideUsers(maxClients).over(5.minutes))
  ).protocols(httpConf)
}

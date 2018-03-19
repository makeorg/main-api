package org.make.api.migrations

import java.time.LocalDate

import org.make.api.migrations.CreateOperation.CountryConfiguration
import org.make.core.reference.TagId

object MVEOperation extends CreateOperation {
  override val operationSlug: String = "mieux-vivre-ensemble"

  override val defaultLanguage: String = "fr"

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      country = "FR",
      language = "fr",
      title = "Comment mieux vivre ensemble ?",
      startDate = LocalDate.parse("2018-03-12"),
      endDate = None,
      tags = Seq(
        TagId("curation"),
        TagId("prevention"),
        TagId("repression"),
        TagId("action-associations"),
        TagId("action-syndicats"),
        TagId("action-entreprises"),
        TagId("action-publique"),
        TagId("cible-citadins"),
        TagId("cible-ruraux"),
        TagId("cible-jeunes"),
        TagId("cible-personnes-agees"),
        TagId("cible-associations"),
        TagId("cible-syndicats"),
        TagId("cible-entreprises"),
        TagId("cible-individus"),
        TagId("cible-elus"),
        TagId("cible-collectivites-territoriales"),
        TagId("cible-etats-gouvernements"),
        TagId("numerique"),
        TagId("engagement-associatif"),
        TagId("partage"),
        TagId("participation-citoyenne"),
        TagId("effort-individuel"),
        TagId("rse"),
        TagId("fiscalite"),
        TagId("aides-subventions"),
        TagId("sanctions"),
        TagId("couverture-sociale"),
        TagId("regulation"),
        TagId("sensibilisation"),
        TagId("education"),
        TagId("laicite"),
        TagId("civisme"),
        TagId("sport"),
        TagId("culture"),
        TagId("solidarites"),
        TagId("sans-abri"),
        TagId("pauvrete"),
        TagId("mixite-sociale"),
        TagId("discriminations"),
        TagId("dialogue"),
        TagId("intergenerationnel"),
        TagId("vieillissement"),
        TagId("jeunesse"),
        TagId("handicap"),
        TagId("urbanisme"),
        TagId("ruralite"),
        TagId("fracture-numerique"),
        TagId("isolement")
      )
    )
  )

  override val runInProduction: Boolean = false
}

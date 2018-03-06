package org.make.api.migrations

import java.time.LocalDate

import org.make.api.migrations.CreateOperation.CountryConfiguration
import org.make.core.reference.TagId

object LpaeOperation extends CreateOperation {
  override val operationSlug: String = "lpae"

  override val defaultLanguage: String = "fr"

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      country = "FR",
      language = "fr",
      tags = Seq(
        TagId("lpae-prevention"),
        TagId("lpae-repression"),
        TagId("lpae-curation"),
        TagId("lpae-democratie"),
        TagId("lpae-efficacite-de-l-etat"),
        TagId("lpae-ecologie"),
        TagId("lpae-agriculture"),
        TagId("lpae-pauvrete"),
        TagId("lpae-chomage"),
        TagId("lpae-conditions-de-travail"),
        TagId("lpae-finance"),
        TagId("lpae-entreprenariat"),
        TagId("lpae-recherche-innovation"),
        TagId("lpae-bouleversements-technologiques"),
        TagId("lpae-jeunesse"),
        TagId("lpae-inegalites"),
        TagId("lpae-discriminations"),
        TagId("lpae-culture"),
        TagId("lpae-vieillissement"),
        TagId("lpae-handicap"),
        TagId("lpae-logement"),
        TagId("lpae-sante"),
        TagId("lpae-solidarites"),
        TagId("lpae-justice"),
        TagId("lpae-sécurite"),
        TagId("lpae-terrorisme"),
        TagId("lpae-developpement"),
        TagId("lpae-guerres"),
        TagId("lpae-ue"),
        TagId("lpae-gouvernance-mondiale"),
        TagId("lpae-conquete-spatiale"),
        TagId("lpae-politique-eco-regulation"),
        TagId("lpae-aides-subventions"),
        TagId("lpae-fiscalite"),
        TagId("lpae-politique-monetaire"),
        TagId("lpae-couverture-sociale"),
        TagId("lpae-medical"),
        TagId("lpae-mobilites"),
        TagId("lpae-sensibilisation"),
        TagId("lpae-education"),
        TagId("lpae-sanctions"),
        TagId("lpae-rse"),
        TagId("lpae-nouvelles-technologies"),
        TagId("lpae-fonctionnement-des-institutions"),
        TagId("lpae-participation-citoyenne"),
        TagId("lpae-cible-etats-gouvernements"),
        TagId("lpae-cible-collectivites-territoriales"),
        TagId("lpae-cible-elus"),
        TagId("lpae-cible-individus"),
        TagId("lpae-cible-entreprises"),
        TagId("lpae-cible-syndicats"),
        TagId("lpae-cible-associations-ong"),
        TagId("lpae-cible-instances-mondiales"),
        TagId("lpae-action-publique"),
        TagId("lpae-action-des-individus"),
        TagId("lpae-action-entreprises"),
        TagId("lpae-action-syndicats"),
        TagId("lpae-action-associations-ong"),
        TagId("lpae-action-instances-mondiales")
      ),
      title = "Vous avez les clés du monde, que changez-vous ?",
      startDate = LocalDate.parse("2018-01-01"),
      endDate = None
    )
  )

  override val runInProduction: Boolean = false
}

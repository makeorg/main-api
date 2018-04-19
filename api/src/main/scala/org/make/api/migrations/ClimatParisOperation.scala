package org.make.api.migrations

import java.time.LocalDate

import org.make.api.migrations.CreateOperation.CountryConfiguration
import org.make.core.tag.TagId

object ClimatParisOperation extends CreateOperation {
  override val operationSlug: String = "climatparis"

  override val defaultLanguage: String = "fr"

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      country = "FR",
      language = "fr",
      tags = Seq(
        TagId("pollution"),
        TagId("entreprises-emploi"),
        TagId("qualite-de-vie"),
        TagId("alimentation"),
        TagId("energies-renouvelables"),
        TagId("bio"),
        TagId("sante"),
        TagId("agriculture"),
        TagId("circuits-courts"),
        TagId("recyclage-zero-dechets"),
        TagId("consommation-responsable"),
        TagId("energies-traditionnelles"),
        TagId("nouvelles-technologies"),
        TagId("urbanisme-habitat"),
        TagId("transports"),
        TagId("fiscalite-subventions"),
        TagId("sensibilisation-education"),
        TagId("solidarite"),
        TagId("action-publique"),
        TagId("participation-citoyenne")
      ),
      title = "Comment lutter contre le changement climatique à Paris ?",
      startDate = LocalDate.parse("2018-01-01"),
      endDate = None
    )
  )

  override val runInProduction: Boolean = false
}

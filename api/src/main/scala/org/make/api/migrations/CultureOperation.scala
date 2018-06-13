package org.make.api.migrations

import java.time.LocalDate

import org.make.api.migrations.CreateOperation.CountryConfiguration

object CultureOperation extends CreateOperation {
  override val operationSlug: String = "culture"

  override val defaultLanguage: String = "fr"

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      country = "FR",
      language = "fr",
      title = "Comment rendre la culture accessible à tous?",
      startDate = LocalDate.parse("2018-06-18"),
      endDate = Some(LocalDate.parse("2018-08-26")),
      tags = Seq.empty
    )
  )

  override val runInProduction: Boolean = true
}

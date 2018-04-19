package org.make.api.migrations

import java.time.LocalDate

import org.make.core.tag.TagId

object MakeEuropeOperation extends CreateOperation {
  override val operationSlug: String = "make-europe"

  override val defaultLanguage: String = "en"

  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CreateOperation.CountryConfiguration(
      country = "GB",
      language = "en",
      title = "Consultation européenne (démo)",
      startDate = LocalDate.parse("2018-03-26"),
      endDate = Some(LocalDate.parse("2018-04-30")),
      tags = Seq(
        TagId("european-commission"),
        TagId("social"),
        TagId("taxes"),
        TagId("environment"),
        TagId("minimum-wage"),
        TagId("finance"),
        TagId("budget"),
        TagId("students"),
        TagId("erasmus")
      )
    )
  )

  override val runInProduction: Boolean = false
}

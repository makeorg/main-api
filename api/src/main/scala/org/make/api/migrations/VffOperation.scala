package org.make.api.migrations
import java.time.LocalDate

import org.make.api.migrations.CreateOperation.CountryConfiguration
import org.make.core.reference.TagId

object VffOperation extends CreateOperation {

  override val operationSlug: String = "vff"
  override val defaultLanguage: String = "fr"
  override val countryConfigurations: Seq[CreateOperation.CountryConfiguration] = Seq(
    CountryConfiguration(
      "FR",
      "fr",
      Seq(
        TagId("signalement"),
        TagId("police-justice"),
        TagId("education-sensibilisation"),
        TagId("image-des-femmes"),
        TagId("independance-financiere"),
        TagId("soutien-psychologique"),
        TagId("hebergement"),
        TagId("transports"),
        TagId("monde-du-travail"),
        TagId("monde-medical"),
        TagId("agissements-sexistes"),
        TagId("violences-sexuelles"),
        TagId("harcelement"),
        TagId("agressions-physiques"),
        TagId("violences-conjugales"),
        TagId("traditions-nefastes-mutilations"),
        TagId("action-publique"),
        TagId("prevention"),
        TagId("protection"),
        TagId("reponses")
      ),
      "comment-lutter-contre-les-violences-faites-aux-femmes",
      endDate = Some(LocalDate.parse("2018-03-01")),
      startDate = LocalDate.parse("2018-01-01")
    ),
    CountryConfiguration(
      "IT",
      "it",
      Seq(
        TagId("avviso"),
        TagId("polizia-giustizia"),
        TagId("mondo-del-lavoro"),
        TagId("trasporti"),
        TagId("azione-pubblica"),
        TagId("sistemazione"),
        TagId("educazione-sensibilizzazione"),
        TagId("sostegno-psicologico"),
        TagId("indipendenza-finanziaria"),
        TagId("comportamento-sessista"),
        TagId("mutilazioni"),
        TagId("violenze-sessuali"),
        TagId("molestia"),
        TagId("tradizioni-dannose"),
        TagId("immagine-della-donna"),
        TagId("violenza-coniugale"),
        TagId("prevenzione"),
        TagId("protezione"),
        TagId("risposte"),
        TagId("aggressione")
      ),
      "Come far fronte alla violenza sulle donne?",
      endDate = Some(LocalDate.parse("2018-05-30")),
      startDate = LocalDate.parse("2018-03-01")
    ),
    CountryConfiguration(
      "GB",
      "en",
      Seq(
        TagId("description"),
        TagId("police-justice"),
        TagId("professional-environment"),
        TagId("transportation"),
        TagId("public-action"),
        TagId("accommodation"),
        TagId("education-awareness"),
        TagId("psychological-support"),
        TagId("financial-independence"),
        TagId("sexist-behaviour"),
        TagId("mutilations"),
        TagId("sexual-violence"),
        TagId("harassment"),
        TagId("harmful-questionnable-traditions"),
        TagId("image-of-women"),
        TagId("domestic-violence"),
        TagId("prevention-en"),
        TagId("protection"),
        TagId("responses"),
        TagId("aggression")
      ),
      "How to combat violence against women?",
      endDate = Some(LocalDate.parse("2018-05-30")),
      startDate = LocalDate.parse("2018-03-01")
    )
  )

  override val runInProduction: Boolean = false
}

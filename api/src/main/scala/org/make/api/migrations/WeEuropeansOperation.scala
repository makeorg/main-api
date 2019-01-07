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

package org.make.api.migrations

import org.make.api.migrations.CreateOperation.QuestionConfiguration
import org.make.core.reference.{Country, Language}

object WeEuropeansOperation extends CreateOperation {
  override val operationSlug: String = "weeuropeans"

  override val defaultLanguage: Language = Language("fr")

  override val questions: Seq[QuestionConfiguration] = Seq(
    QuestionConfiguration(
      country = Country("AT"),
      language = Language("de"),
      slug = "weeuropeans-at",
      title = "WeEuropeans",
      question = "Wie können wir Europa konkret neu gestalten?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("BE"),
      language = Language("nl"),
      slug = "weeuropeans-be-nl",
      title = "WeEuropeans",
      question = "Hoe kunnen we Europa concreet vernieuwen?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("BE"),
      language = Language("fr"),
      slug = "weeuropeans-be-fr",
      title = "WeEuropeans",
      question = "Comment réinventer l'Europe, concrètement ?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("BG"),
      language = Language("bg"),
      slug = "weeuropeans-bg",
      title = "WeEuropeans",
      question = "Как конкретно да преоткрием Европа?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("CY"),
      language = Language("el"),
      slug = "weeuropeans-cy",
      title = "WeEuropeans",
      question = "Πώς να θέσουμε νέες και σταθερές βάσεις στην Ευρώπη;",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("CZ"),
      language = Language("cs"),
      slug = "weeuropeans-cz",
      title = "WeEuropeans",
      question = "Jak konkrétně dát Evropě nový smysl?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("DE"),
      language = Language("de"),
      slug = "weeuropeans-de",
      title = "WeEuropeans",
      question = "Wie können wir Europa konkret neu gestalten?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("DK"),
      language = Language("da"),
      slug = "weeuropeans-dk",
      title = "WeEuropeans",
      question = "Hvordan kan vi genopfinde Europa, helt konkret?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("EE"),
      language = Language("et"),
      slug = "weeuropeans-ee",
      title = "WeEuropeans",
      question = "Kuidas konkreetselt Euroopa uueks luua?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("ES"),
      language = Language("es"),
      slug = "weeuropeans-es",
      title = "WeEuropeans",
      question = "¿Cómo podemos exactamente reinventar Europa?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("FI"),
      language = Language("fi"),
      slug = "weeuropeans-fi",
      title = "WeEuropeans",
      question = "Mihin toimenpiteisiin voimme ryhtyä Euroopan uudistamiseksi?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("FR"),
      language = Language("fr"),
      slug = "weeuropeans-fr",
      title = "WeEuropeans",
      question = "Comment réinventer l'Europe, concrètement ?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("GR"),
      language = Language("el"),
      slug = "weeuropeans-gr",
      title = "WeEuropeans",
      question = "Πώς να θέσουμε νέες και σταθερές βάσεις στην Ευρώπη;",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("HR"),
      language = Language("hr"),
      slug = "weeuropeans-hr",
      title = "WeEuropeans",
      question = "Kako konkretno udahnuti novi život Europi?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("HU"),
      language = Language("hu"),
      slug = "weeuropeans-hu",
      title = "WeEuropeans",
      question = "Pontosan hogyan kellene újragondolni Európát?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("IE"),
      language = Language("en"),
      slug = "weeuropeans-ie",
      title = "WeEuropeans",
      question = "What are the concrete steps we can take to reinvent Europe?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("IT"),
      language = Language("it"),
      slug = "weeuropeans-it",
      title = "WeEuropeans",
      question = "Come reinventare l’Europa, in concreto?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("LT"),
      language = Language("lt"),
      slug = "weeuropeans-lt",
      title = "WeEuropeans",
      question = "Kaip iš naujo atrasti Europą? Konkrečiai.",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("LU"),
      language = Language("fr"),
      slug = "weeuropeans-lu",
      title = "WeEuropeans",
      question = "Comment réinventer l'Europe, concrètement ?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("LV"),
      language = Language("lv"),
      slug = "weeuropeans-lv",
      title = "WeEuropeans",
      question = "Kā tieši pārveidot Eiropu?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("MT"),
      language = Language("mt"),
      slug = "weeuropeans-mt",
      title = "WeEuropeans",
      question = "Kif nistgħu noħolqu l-Ewropa mill-ġdid, b’mod konkret?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("NL"),
      language = Language("nl"),
      slug = "weeuropeans-nl",
      title = "WeEuropeans",
      question = "Hoe kunnen we Europa verbeteren?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("PL"),
      language = Language("pl"),
      slug = "weeuropeans-pl",
      title = "WeEuropeans",
      question = "Jak – konkretnie – zmienić Europę?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("PT"),
      language = Language("pt"),
      slug = "weeuropeans-pt",
      title = "WeEuropeans",
      question = "Como reinventar a Europa de forma concreta?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("RO"),
      language = Language("ro"),
      slug = "weeuropeans-ro",
      title = "WeEuropeans",
      question = "Cum se reinventează concret Europa?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("SE"),
      language = Language("sv"),
      slug = "weeuropeans-se",
      title = "WeEuropeans",
      question = "Hur kan man konkret återuppfinna Europa?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("SI"),
      language = Language("sl"),
      slug = "weeuropeans-si",
      title = "WeEuropeans",
      question = "Kako preoblikovati Europo, konkretno?",
      startDate = None,
      endDate = None
    ),
    QuestionConfiguration(
      country = Country("SK"),
      language = Language("sk"),
      slug = "weeuropeans-sk",
      title = "WeEuropeans",
      question = "Ako konkrétne dať nový zmysel Európe?",
      startDate = None,
      endDate = None
    )
  )

  override val allowedSources: Seq[String] = Seq("core")

  override val runInProduction: Boolean = true
}

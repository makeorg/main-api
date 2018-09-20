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

package org.make.core.user

import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.ElasticDsl
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.sort.{FieldSortDefinition, SortOrder}
import com.sksamuel.elastic4s.searches.suggestion.Fuzziness
import org.make.core.reference.{Country, Language}
import org.make.core.user.indexed.OrganisationElasticsearchFieldNames

/**
  * The class holding the entire search query
  *
  * @param filters organisation of search filters
  * @param limit   number of items to fetch
  * @param skip    number of items to skip
  */
case class OrganisationSearchQuery(filters: Option[OrganisationSearchFilters] = None,
                                   limit: Option[Int] = None,
                                   skip: Option[Int] = None,
                                   sort: Option[String] = None,
                                   order: Option[String] = None)

/**
  * The class holding the filters
  *
  * @param organisationIds   The organisationIds to filter
  * @param organisationName  Name of the organisation to search
  * @param slug              Slug to filter
  * @param description       Description to search
  * @param country           Country to filter
  * @param language          Language to filter
  */
case class OrganisationSearchFilters(organisationIds: Option[OrganisationIdsSearchFilter] = None,
                                     organisationName: Option[OrganisationNameSearchFilter] = None,
                                     slug: Option[SlugSearchFilter] = None,
                                     description: Option[DescriptionSearchFilter] = None,
                                     country: Option[CountrySearchFilter] = None,
                                     language: Option[LanguageSearchFilter] = None)

object OrganisationSearchFilters extends ElasticDsl {

  def parse(organisationIds: Option[OrganisationIdsSearchFilter] = None,
            organisationName: Option[OrganisationNameSearchFilter] = None,
            slug: Option[SlugSearchFilter] = None,
            description: Option[DescriptionSearchFilter] = None,
            country: Option[CountrySearchFilter] = None,
            language: Option[LanguageSearchFilter] = None): Option[OrganisationSearchFilters] = {

    (organisationIds, organisationName, slug, description, country, language) match {
      case (None, None, None, None, None, None) => None
      case _ =>
        Some(OrganisationSearchFilters(organisationIds, organisationName, slug, description, country, language))
    }
  }

  /**
    * Build elasticsearch search filters from searchQuery
    *
    * @param organisationSearchQuery search query
    *
    * @return sequence of query definitions
    */
  def getOrganisationSearchFilters(organisationSearchQuery: OrganisationSearchQuery): Seq[QueryDefinition] =
    Seq(
      buildOrganisationIdsSearchFilter(organisationSearchQuery),
      buildOrganisationNameSearchFilter(organisationSearchQuery),
      buildSlugSearchFilter(organisationSearchQuery),
      buildDescriptionSearchFilter(organisationSearchQuery),
      buildCountrySearchFilter(organisationSearchQuery),
      buildLanguageSearchFilter(organisationSearchQuery)
    ).flatten

  def getSkipSearch(organisationSearchQuery: OrganisationSearchQuery): Int =
    organisationSearchQuery.skip
      .getOrElse(0)

  def getLimitSearch(organisationSearchQuery: OrganisationSearchQuery): Int =
    organisationSearchQuery.limit
      .getOrElse(-1) // TODO get default value from configurations

  def getSort(organisationSearchQuery: OrganisationSearchQuery): Option[FieldSortDefinition] = {
    val order = organisationSearchQuery.order.map {
      case asc if asc.toLowerCase == "asc"    => SortOrder.ASC
      case desc if desc.toLowerCase == "desc" => SortOrder.DESC
    }

    organisationSearchQuery.sort.map { sort =>
      val sortFieldName: String = if (sort == OrganisationElasticsearchFieldNames.organisationName) {
        OrganisationElasticsearchFieldNames.organisationNameKeyword
      } else {
        sort
      }
      FieldSortDefinition(field = sortFieldName, order = order.getOrElse(SortOrder.ASC))
    }
  }

  def buildOrganisationIdsSearchFilter(organisationSearchQuery: OrganisationSearchQuery): Option[QueryDefinition] = {
    organisationSearchQuery.filters.flatMap {
      _.organisationIds match {
        case Some(OrganisationIdsSearchFilter(Seq(organisationId))) =>
          Some(ElasticApi.termQuery(OrganisationElasticsearchFieldNames.organisationId, organisationId.value))
        case Some(OrganisationIdsSearchFilter(organisationIds)) =>
          Some(ElasticApi.termsQuery(OrganisationElasticsearchFieldNames.organisationId, organisationIds.map(_.value)))
        case _ => None
      }
    }
  }

  def buildOrganisationNameSearchFilter(organisationSearchQuery: OrganisationSearchQuery): Option[QueryDefinition] = {
    val query: Option[QueryDefinition] = for {
      filters                                        <- organisationSearchQuery.filters
      OrganisationNameSearchFilter(text, maybeFuzzy) <- filters.organisationName
    } yield {
      maybeFuzzy match {
        case Some(fuzzy) =>
          ElasticApi
            .should(
              matchQuery(OrganisationElasticsearchFieldNames.organisationName, text)
                .boost(2F),
              matchQuery(OrganisationElasticsearchFieldNames.organisationName, text)
                .fuzziness(fuzzy.toString)
                .boost(1F)
            )
        case None => ElasticApi.matchQuery(OrganisationElasticsearchFieldNames.organisationName, text)
      }
    }

    query match {
      case None => None
      case _    => query
    }
  }

  def buildSlugSearchFilter(organisationSearchQuery: OrganisationSearchQuery): Option[QueryDefinition] = {
    organisationSearchQuery.filters.flatMap {
      _.slug match {
        case Some(SlugSearchFilter(slug)) =>
          Some(ElasticApi.termQuery(OrganisationElasticsearchFieldNames.slug, slug))
        case _ => None
      }
    }
  }

  def buildDescriptionSearchFilter(organisationSearchQuery: OrganisationSearchQuery): Option[QueryDefinition] = {
    organisationSearchQuery.filters.flatMap {
      _.description match {
        case Some(DescriptionSearchFilter(description)) =>
          Some(ElasticApi.matchQuery(OrganisationElasticsearchFieldNames.description, description))
        case _ => None
      }
    }
  }

  def buildCountrySearchFilter(organisationSearchQuery: OrganisationSearchQuery): Option[QueryDefinition] = {
    organisationSearchQuery.filters.flatMap {
      _.country match {
        case Some(CountrySearchFilter(country)) =>
          Some(ElasticApi.termsQuery(OrganisationElasticsearchFieldNames.country, country.value))
        case _ => None
      }
    }
  }

  def buildLanguageSearchFilter(organisationSearchQuery: OrganisationSearchQuery): Option[QueryDefinition] = {
    organisationSearchQuery.filters.flatMap {
      _.language match {
        case Some(LanguageSearchFilter(language)) =>
          Some(ElasticApi.termsQuery(OrganisationElasticsearchFieldNames.language, language.value))
        case _ => None
      }
    }
  }
}

case class OrganisationIdsSearchFilter(organisationIds: Seq[UserId])
case class OrganisationNameSearchFilter(text: String, fuzzy: Option[Fuzziness] = None)
case class SlugSearchFilter(slug: String)
case class DescriptionSearchFilter(description: String)
case class CountrySearchFilter(country: Country)
case class LanguageSearchFilter(language: Language)
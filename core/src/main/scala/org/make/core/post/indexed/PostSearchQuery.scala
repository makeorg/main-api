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

package org.make.core
package post
package indexed

import com.sksamuel.elastic4s.{ElasticApi, ElasticDsl}
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import org.make.core.reference.Country

final case class PostSearchQuery(
  filters: Option[PostSearchFilters] = None,
  limit: Option[Int] = None,
  skip: Option[Int] = None,
  sort: Option[String] = None,
  order: Option[Order] = None
)

final case class PostSearchFilters(
  postIds: Option[PostIdsSearchFilter] = None,
  displayHome: Option[DisplayHomeSearchFilter] = None,
  country: Option[PostCountryFilter] = None
)

object PostSearchFilters extends ElasticDsl {

  def parse(
    postIds: Option[PostIdsSearchFilter] = None,
    displayHome: Option[DisplayHomeSearchFilter] = None,
    postCountry: Option[PostCountryFilter] = None
  ): Option[PostSearchFilters] = {
    (postIds, displayHome, postCountry) match {
      case (None, None, None) => None
      case _                  => Some(PostSearchFilters(postIds, displayHome, postCountry))
    }
  }

  def getPostSearchFilters(postSearchQuery: PostSearchQuery): Seq[Query] = {
    Seq(
      buildPostIdsSearchFilter(postSearchQuery.filters),
      buildDisplayHomeSearchFilter(postSearchQuery.filters),
      buildPostCountrySearchFilter(postSearchQuery.filters)
    ).flatten
  }

  def getSkipSearch(postSearchQuery: PostSearchQuery): Int =
    postSearchQuery.skip.getOrElse(0)

  def getLimitSearch(postSearchQuery: PostSearchQuery): Int =
    postSearchQuery.limit.getOrElse(10)

  def getSort(postSearchQuery: PostSearchQuery): Option[FieldSort] = {
    val order: SortOrder = postSearchQuery.order.map(_.sortOrder).getOrElse(SortOrder.Asc)

    postSearchQuery.sort.map { sort =>
      FieldSort(field = sort, order = order)
    }
  }

  def buildPostIdsSearchFilter(maybeFilters: Option[PostSearchFilters]): Option[Query] = {
    for {
      filters                      <- maybeFilters
      PostIdsSearchFilter(postIds) <- filters.postIds
    } yield ElasticApi.termsQuery(PostElasticsearchFieldNames.postId, postIds.map(_.value))
  }

  def buildDisplayHomeSearchFilter(maybeFilters: Option[PostSearchFilters]): Option[Query] = {
    for {
      filters                              <- maybeFilters
      DisplayHomeSearchFilter(displayHome) <- filters.displayHome
    } yield ElasticApi.termQuery(PostElasticsearchFieldNames.displayHome, displayHome)
  }

  def buildPostCountrySearchFilter(maybeFilters: Option[PostSearchFilters]): Option[Query] = {
    for {
      filters                    <- maybeFilters
      PostCountryFilter(country) <- filters.country
    } yield ElasticApi.termQuery(PostElasticsearchFieldNames.country, country.value)
  }

}

final case class PostIdsSearchFilter(postIds: Seq[PostId])
final case class DisplayHomeSearchFilter(displayHome: Boolean)
final case class PostCountryFilter(country: Country)

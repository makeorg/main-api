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

package org.make.api.organisation

import akka.Done
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.RefreshPolicy
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.queries.compound.BoolQuery
import grizzled.slf4j.Logging
import io.circe.{Json, Printer}
import org.make.api.technical.elasticsearch.{ElasticsearchConfigurationComponent, _}
import org.make.core.CirceFormatters
import org.make.core.user.indexed.{IndexedOrganisation, OrganisationSearchResult}
import org.make.core.user.{OrganisationSearchFilters, OrganisationSearchQuery, SlugSearchFilter, UserId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait OrganisationSearchEngineComponent {
  def elasticsearchOrganisationAPI: OrganisationSearchEngine
}

trait OrganisationSearchEngine {
  def findOrganisationById(organisationId: UserId): Future[Option[IndexedOrganisation]]
  def findOrganisationBySlug(slug: String): Future[Option[IndexedOrganisation]]
  def searchOrganisations(query: OrganisationSearchQuery): Future[OrganisationSearchResult]
  def indexOrganisation(record: IndexedOrganisation, mayBeIndex: Option[Index] = None): Future[Done]
  def indexOrganisations(records: Seq[IndexedOrganisation], mayBeIndex: Option[Index] = None): Future[Done]
  def updateOrganisation(record: IndexedOrganisation, mayBeIndex: Option[Index] = None): Future[Done]
}

object OrganisationSearchEngine {
  val organisationIndexName: String = "organisation"
}

trait DefaultOrganisationSearchEngineComponent extends OrganisationSearchEngineComponent with CirceFormatters {
  self: ElasticsearchConfigurationComponent with ElasticsearchClientComponent =>

  override lazy val elasticsearchOrganisationAPI: OrganisationSearchEngine = new DefaultOrganisationSearchEngine

  class DefaultOrganisationSearchEngine extends OrganisationSearchEngine with Logging {

    private lazy val client = elasticsearchClient.client

    private val organisationAlias: Index =
      elasticsearchConfiguration.organisationAliasName

    // TODO remove once elastic4s-circe upgrades to circe 0.14
    private implicit val printer: Json => String = Printer.noSpaces.print

    override def findOrganisationById(organisationId: UserId): Future[Option[IndexedOrganisation]] = {
      client
        .executeAsFuture(get(organisationAlias, organisationId.value))
        .map(_.toOpt[IndexedOrganisation])
    }

    override def findOrganisationBySlug(slug: String): Future[Option[IndexedOrganisation]] = {
      val query = OrganisationSearchQuery(
        filters = OrganisationSearchFilters.parse(slug = Some(SlugSearchFilter(slug))),
        limit = Some(1)
      )
      val searchFilters = OrganisationSearchFilters.getOrganisationSearchFilters(query)
      val request = search(organisationAlias)
        .bool(BoolQuery(must = searchFilters))
        .from(0)
        .size(1)
        .trackTotalHits(true)

      client.executeAsFuture {
        request
      }.map(_.to[IndexedOrganisation]).map(_.headOption)
    }

    override def searchOrganisations(query: OrganisationSearchQuery): Future[OrganisationSearchResult] = {
      val searchFilters = OrganisationSearchFilters.getOrganisationSearchFilters(query)
      val request: SearchRequest = search(organisationAlias)
        .bool(BoolQuery(must = searchFilters))
        .sortBy(OrganisationSearchFilters.getSort(query).toList)
        .size(OrganisationSearchFilters.getLimitSearch(query))
        .from(OrganisationSearchFilters.getSkipSearch(query))
        .trackTotalHits(true)

      val requestSorted = query.sortAlgorithm match {
        case None                => request
        case Some(sortAlgorithm) => sortAlgorithm.sortDefinition(request)
      }

      client
        .executeAsFuture(requestSorted)
        .map { response =>
          OrganisationSearchResult(total = response.totalHits, results = response.to[IndexedOrganisation])
        }
    }

    override def indexOrganisation(record: IndexedOrganisation, mayBeIndex: Option[Index]): Future[Done] = {
      val index = mayBeIndex.getOrElse(organisationAlias)
      client
        .executeAsFuture(indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.organisationId.value))
        .map { _ =>
          Done
        }
    }

    override def indexOrganisations(records: Seq[IndexedOrganisation], mayBeIndex: Option[Index]): Future[Done] = {
      val index = mayBeIndex.getOrElse(organisationAlias)
      client
        .executeAsFuture(bulk(records.map { record =>
          indexInto(index).doc(record).refresh(RefreshPolicy.IMMEDIATE).id(record.organisationId.value)
        }))
        .map { _ =>
          Done
        }
    }

    override def updateOrganisation(record: IndexedOrganisation, mayBeIndex: Option[Index]): Future[Done] = {
      val index = mayBeIndex.getOrElse(organisationAlias)
      client
        .executeAsFuture(
          updateById(index, record.organisationId.value)
            .doc(record)
            .refresh(RefreshPolicy.IMMEDIATE)
        )
        .map(_ => Done)
    }

  }
}
